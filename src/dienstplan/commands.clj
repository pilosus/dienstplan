;; Copyright (c) 2021-2022 Vitaly Samigullin and contributors. All rights reserved.
;;
;; This program and the accompanying materials are made available under the
;; terms of the Eclipse Public License 2.0 which is available at
;; http://www.eclipse.org/legal/epl-2.0.
;;
;; This Source Code may also be made available under the following Secondary
;; Licenses when the conditions for such availability set forth in the Eclipse
;; Public License, v. 2.0 are satisfied: GNU General Public License as published by
;; the Free Software Foundation, either version 2 of the License, or (at your
;; option) any later version, with the GNU Classpath Exception which is available
;; at https://www.gnu.org/software/classpath/license.html.
;;
;; SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

(ns dienstplan.commands
  (:gen-class)
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.db :as db]
   [dienstplan.spec :as spec]))

;; Const

(def slack-api-post-msg
  "https://slack.com/api/chat.postMessage")

(def help-msg
  "To start interacting with the bot, mention its username, provide a command and its arguments as follows:

```
@dienstplan <command> [<options>]
```

Commands:

1. Create a rotation
```
@dienstplan create <rotation name> <list of users> <duties description>
```

2. Rotate: take the next user on the rotation list
```
@dienstplan rotate <rotation name>
```

3. Show who is duty
```
@dienstplan who <rotation name>
```

4. Delete a rotation
```
@dienstplan delete <rotation name>
```

5. List channel's rotations
```
@dienstplan list
```

6. Show help message
```
@dienstplan help
```

Example:

Let's create a rotation using dienstplan:

```
@dienstplan create backend-rota @user1 @user2 @user3
Backend engineer's duties:
- Process support team questions queue
- Resolve service alerts
- Check service health metrics
- Casual code refactoring
- Follow the boy scout rule: always leave the campground cleaner than you found it
```

Now let's use Slack reminder to rotate weekly:

```
/remind #my-channel to \"@dienstplan rotate backend-rota\" every Monday at 9AM UTC
```

Let's also show a current duty engineer with a reminder:

```
/remind #my-channel to \"@dienstplan who backend-rota\" every Monday, Tuesday, Wednesday, Thursday, Friday at 10AM UTC
```
")

(def help-cmd-create
  "Usage:
@dienstplan create <rotation name> <list of users> <duties description>

Example:
@dienstplan create backend-rota @user1 @user2 @user3
Backend engineer's duties:
- Process support team questions queue
- Resolve service alerts
- Check service health metrics
- Casual code refactoring
- Follow the boy scout rule: always leave the campground cleaner than you found it")

(def help-cmd-rotate
  "Usage:
@dienstplan rotate <rotation name>

Example:
@dienstplan rotate backend-rota")

(def help-cmd-who
  "Usage:
@dienstplan who <rotation name>

Example:
@dienstplan who backend-rota")

(def help-cmd-delete
  "Usage:
@dienstplan delete <rotation name>

Example:
@dienstplan delete backend-rota")

(def help-cmd-list
  "Usage:
@dienstplan list")

(def help-cmd-help
  "Usage:
@dienstplan help")

(def regex-user-mention #"(?s)(?<userid>\<@[A-Z0-9]+\>)")

(def regex-channel-mention #"(?s)(?<channelid>\<#[A-Z0-9]+\>)")

(def regex-app-mention
  "(?s) is a pattern flag for dot matching all symbols including newlines"
  #"^[^<@]*(?s)(?<userid><@[^>]+>)[\u00A0|\u2007|\u202F|\s]+(?<command>\w+)[\u00A0|\u2007|\u202F|\s]*(?<rest>.*)")

(def commands->data
  {:create {:spec ::spec/bot-cmd-create
            :help help-cmd-create}
   :rotate {:spec ::spec/bot-cmd-default
            :help help-cmd-rotate}
   :delete {:spec ::spec/bot-cmd-default
            :help help-cmd-delete}
   :who {:spec ::spec/bot-cmd-default
         :help help-cmd-who}
   :list {:spec ::spec/bot-cmd-list
          :help help-cmd-list}
   :help {:spec ::spec/bot-cmd-help
          :help help-cmd-help}})

;; Helpers

(defn keyword->command
  [kw]
  (if (get commands->data kw) kw nil))

(defn nilify
  [s]
  (if (str/blank? s) nil s))

(defn stringify
  [s]
  (or s ""))

(defn str-trim
  "Trim a string with extra three whitespace chars unsupported by Java regex"
  [s]
  (-> s
      (str/replace #"^[\u00A0|\u2007|\u202F|\s|\.]*" "")
      (str/replace #"[\u00A0|\u2007|\u202F|\s|\.]*$" "")))

(defn text-trim
  ""
  [s]
  (-> s
      (str/replace #"[,!?\-\.]*$" "")
      str/trim))

(defn slack-mention-channel
  "Make channel name formatted for Slack API"
  [channel]
  (if (re-matches regex-channel-mention channel)
    channel
    (format "<#%s>" channel)))

;; TODO use s/conform
(defn get-event-app-mention
  [request]
  (let [event (get-in request [:params :event])
        {:keys [text team channel ts]} event]
    {:channel channel
     :team team
     :ts ts
     :text text}))

(defn get-now-ts
  []
  (new java.sql.Timestamp (System/currentTimeMillis)))

;; Parse app mention response

(defn parse-app-mention
  "Parse text from app_mention response.
  Channels, users, and links are expected to be escaped with the angle brakets"
  [raw-text]
  (let [text
        (->>
         raw-text
         stringify
         text-trim)
        matcher (re-matcher regex-app-mention text)
        result
        (if (.matches matcher)
          {:command
           (->>
            (.group matcher "command")
            nilify
            keyword
            keyword->command)
           :rest (->> (.group matcher "rest") nilify)}
          nil)]
    result))

(defn parse-user-mentions
  "Extract a seq of user-ids mentioned in the string"
  [raw-text]
  (let [text
        (->>
         raw-text
         stringify
         str/trim)
        users (re-seq regex-user-mention text)
        result (if users (map #(second %) users) nil)]
    result))

;; Parse command arguments

(defn get-command-args
  [app-mention]
  (->>
   (get app-mention :rest)
   stringify
   str/trim))

(defmulti parse-args
  "Parse command arguments for app mention"
  (fn [app-mention] (get app-mention :command)))

(defmethod parse-args :create [app-mention]
  (let [args (get-command-args app-mention)
        splitted (str/split args regex-user-mention)
        name (->
              (first splitted)
              str-trim)
        description (->> (last splitted) str-trim)
        users (parse-user-mentions args)]
    {:name name
     :users users
     :description description}))

(defn- parse-args-default
  "Parse arguments for simple commands in the form: command <name>"
  [app-mention]
  (let [name (nilify (get-command-args app-mention))
        result (if name {:name name} nil)]
    result))

(defmethod parse-args :rotate [app-mention]
  (parse-args-default app-mention))

(defmethod parse-args :delete [app-mention]
  (parse-args-default app-mention))

(defmethod parse-args :who [app-mention]
  (parse-args-default app-mention))

(defmethod parse-args :help [_]
  {:description help-msg})

(defmethod parse-args :default [_] nil)

;; Actions

(defmulti command-exec!
  "Execute the command"
  (fn [command-map] (:command command-map)))

(defn users->mention-table-rows
  "Add :duty true to the first element of the users list"
  [users]
  (->> users
       (reduce #(conj %1 {:name %2}) [])
       (map-indexed
        (fn [idx v]
          (assoc v :duty (if (= idx 0) true false))))))

(defn- get-channel-rotation [command-map]
  (let [channel (get-in command-map [:context :channel])
        rotation (get-in command-map [:args :name])]
    {:channel channel :rotation rotation}))

(defmethod command-exec! :who [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        rota (first (db/duty-get channel rotation))
        {:keys [duty description]} rota
        text
        (if duty
          (format
           "Hey %s, you are an on-call person for `%s` rotation.\n%s"
           duty rotation description)
          (format
           "Rotation `%s` not found in channel %s"
           rotation (slack-mention-channel channel)))]
    text))

(defmethod command-exec! :delete [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        deleted? (> (first (db/rota-delete! channel rotation)) 0)
        text
        (if deleted?
          (format "Rotation `%s` has been deleted" rotation)
          (format
           "Rotation `%s` not found in channel %s"
           rotation (slack-mention-channel channel)))]
    text))

(defmethod command-exec! :create [command-map]
  (let [now (get-now-ts)
        {:keys [channel rotation]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        users (get-in command-map [:args :users])
        mentions (users->mention-table-rows users)
        rota-params {:channel channel
                     :name rotation
                     :description (get-in command-map [:args :description])
                     :created_on now
                     :updated_on now
                     :meta command-map}
        mention-params mentions
        params {:rota rota-params :mention mention-params}
        inserted (db/rota-insert! params)
        error (:error inserted)
        error-msg (:message error)
        duplicate? (= (:reason error) :duplicate)
        result
        (cond
          duplicate?
          (format
           "Rotation `%s` for channel %s already exists"
           rotation channel-formatted)
          error
          (do
            (log/error error-msg)
            (format
             "Cannot create rotation `%s` for channel %s: %s"
             rotation channel-formatted error-msg))
          :else
          (format
           "Rotation `%s` for channel %s created successfully"
           rotation channel-formatted))]
    result))

(defmethod command-exec! :list [command-map]
  (let [{:keys [channel _]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        rotations (db/rota-list-get channel)
        rota-list
        (->>
         rotations
         (map #(format "- `%s` [%s]" (:name %) (:created_on %)))
         (str/join \newline)
         nilify)
        text
        (if rota-list
          (format "Rotations created in channel %s:\n%s" channel-formatted rota-list)
          (format "No rotations found in channel %s" channel-formatted))]
    text))

(defmethod command-exec! :rotate [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        {:keys [users-count users-updated]}
        (db/rotate-duty! channel rotation (get-now-ts))
        text
        (cond
          (= users-count users-updated)
          (format "Users in rotation `%s` of channel %s have been rotated"
                  rotation channel-formatted)
          :else
          (do
            (log/error
             (format "Updated %s/%s for rotation %s of channel %s"
                     users-updated users-count rotation channel))
            (format "Failed to rotate users in rotation `%s` of channel %s"
                    rotation channel-formatted)))]
    text))

(defmethod command-exec! :default [_] help-msg)

;; Entry point

(defn get-command-map
  "Get parsed command map from app_mention request"
  [request]
  (let [{:keys [text channel team ts]} (get-event-app-mention request)
        context {:channel channel :team team :ts ts}
        parsed-command (parse-app-mention text)
        {:keys [command]} parsed-command
        {:keys [spec help]} (get commands->data command)
        args (parse-args parsed-command)
        command-map
        (if parsed-command
          {:context context
           :command command
           :args args}
          nil)
        result
        (cond
          (nil? command) {:context context :error help-msg}
          (not (s/valid? spec command-map)) (assoc command-map :error help)
          :else command-map)]
    result))

(defn get-command-response
  "Get response for app mention request"
  [request]
  (let [command-map (get-command-map request)
        _ (log/info (format "Parsed command: %s" command-map))
        channel (get-in command-map [:context :channel])
        error (:error command-map)
        text (or error (command-exec! command-map))
        response {:channel channel :text text}]
    response))

;; TODO create a mount http connection pool
;; https://github.com/dakrone/clj-http#persistent-connections
(defn send-command-response
  [request]
  (let [body-map (get-command-response request)
        body-json (json/generate-string body-map)
        token (str "Bearer " (get-in config [:slack :token]))
        params
        {:headers
         {"Authorization" token
          "Content-type" "application/json; charset=utf-8"}
         :body body-json
         :max-redirects 2
         :socket-timeout 1000 ;; ms
         :connection-timeout 1000}
        response-raw (http/post slack-api-post-msg params)
        response-body (:body response-raw)
        response-status (:status response-raw)
        response-data (json/parse-string response-body)
        response-ok? (get response-data "ok")
        log-level (if response-ok? :debug :error)
        log-msg
        (format
         "Post message to Slack: status %s body %s"
         response-status response-data)
        _ (log/log log-level log-msg)]
    body-map))
