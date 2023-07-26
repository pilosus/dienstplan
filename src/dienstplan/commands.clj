;; Copyright (c) Vitaly Samigullin and contributors. All rights reserved.
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
   [clojure.spec.alpha :as s]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [dienstplan.config :refer [config]]
   [dienstplan.db :as db]
   [dienstplan.helpers :as helpers]
   [dienstplan.slack :as slack]
   [dienstplan.spec :as spec]
   [org.pilosus.kairos :as kairos]))

;; Const

(def help-intro "`dienstplan` [version %s] is a bot app for duty rotations.\n\n%s")

(def help-msg
  "To start interacting with the bot, mention its username, provide a command and options as follows:

```
@dienstplan <command> [<options>]
```

Commands:

1. Create a rotation
```
@dienstplan create <rotation name> <list of user mentions> <duties description>
```

2. Rotate: move current duty to a next user
```
@dienstplan rotate <rotation name>
```

3. Show a current duty (on-call person and duties description)
```
@dienstplan who <rotation name>
```

4. Mention current on-call person. It's like `who` command, but with duties description omitted.
```
@dienstplan shout <rotation name>
```

5. Assign specific user for a duty. New user becomes a current on-call and the order of users remains as it was.
```
@dienstplan assign <rotation name> <user mention>
```

6. Show details about a rotation
```
@dienstplan about <rotation name>
```

7. Delete a rotation
```
@dienstplan delete <rotation name>
```

8. Update a rotation. A shortcut to a sequence of `delete` and `create` commands.
```
@dienstplan update <rotation name> <list of user mentions> <duties description>
```

9. List channel's rotations
```
@dienstplan list
```

10. Show a help message
```
@dienstplan help
```

Example:

Let's create a rotation using dienstplan:

```
@dienstplan create my-rota @user1 @user2 @user3
On-call engineer's duties:
- Process support team questions queue
- Resolve service alerts
- Check service health metrics
- Casual code refactoring
- Follow the boy scout rule: always leave the campground cleaner than you found it
```

Now let's use Slack reminder to rotate weekly:

```
/remind #my-channel to \"@dienstplan rotate my-rota\" every Monday at 9AM UTC
```

Let's also show a current duty engineer with a reminder:

```
/remind #my-channel to \"@dienstplan who my-rota\" every Monday, Tuesday, Wednesday, Thursday, Friday at 10AM UTC
```
")

(def help-cmd-create
  "Usage:
```
@dienstplan create <rotation name> <list of user mentions> <duties description>
```

Example:
```
@dienstplan create my-rota @user1 @user2 @user3
On-call engineer's duties:
- Process support team questions queue
- Resolve service alerts
- Check service health metrics
- Casual code refactoring
- Follow the boy scout rule: always leave the campground cleaner than you found it
```")

(def help-cmd-update
  "Usage:
```
@dienstplan update <rotation name> <list of user mentions> <duties description>
```

Example:
```
@dienstplan update my-rota @user1 @user2 @user3 My updated description of the existing rota
```")

(def help-cmd-rotate
  "Usage:
```
@dienstplan rotate <rotation name>
```

Example:
```
@dienstplan rotate my-rota
```")

(def help-cmd-assign
  "Usage:
```
@dienstplan assign <rotation name> <user mention>
```

Example:
```
@dienstplan assign my-rota @user1
```")

(def help-cmd-who
  "Usage:
```
@dienstplan who <rotation name>
```

Example:
```
@dienstplan who my-rota
```")

(def help-cmd-shout
  "Usage:
```
@dienstplan shout <rotation name>
```

Example:
```
@dienstplan shout my-rota
```")

(def help-cmd-delete
  "Usage:
```
@dienstplan delete <rotation name>
```

Example:
```
@dienstplan delete my-rota
```")

(def help-cmd-about
  "Usage:
```
@dienstplan about <rotation name>
```

Example:
```
@dienstplan about my-rota
```")

(def help-cmd-list
  "Usage:
```
@dienstplan list
```")

(def help-cmd-schedule
  "Usage:
```
@dienstplan schedule <subcommand> \"<executable>\" <crontab>
```

where <subcommand> is one of: [create, delete, list]
      \"<executalbe>\" is a command for a bot to run on schedule
      <crontab> is a crontab file line, e.g. `9 0 * * Mon-Fri`

Example:

```
@dienstplan schedule create \"rotate my-rota\" 7 0 * * Mon-Fri
@dienstplan schedule delete \"rotate my-rota\"
@dienstplan schedule list
```

Caveats:
\"<executable>\" must be enclosed into double quotation marks
")

(def help-cmd-help
  "Usage:
```
@dienstplan help
```")

(def regex-user-mention #"(?s)(?<userid>\<@[A-Z0-9]+\>)")

(def regex-channel-mention #"(?s)(?<channelid>\<#[A-Z0-9]+\>)")

(def regex-app-mention
  "(?s) is a pattern flag for dot matching all symbols including newlines"
  #"^[^<@]*(?s)(?<userid><@[^>]+>)[\u00A0|\u2007|\u202F|\s]+(?<command>\w+)[\u00A0|\u2007|\u202F|\s]*(?<rest>.*)")

(def regex-schedule #"(?s)\b(?<subcommand>create|delete|list)[\u00A0|\u2007|\u202F|\s]*(?<enclosed>\"(?<executable>.*)\")?[\u00A0|\u2007|\u202F|\s]*(?<crontab>.*)?")

(def commands->data
  {:create {:spec ::spec/bot-cmd-create-or-update
            :help help-cmd-create}
   :update {:spec ::spec/bot-cmd-create-or-update
            :help help-cmd-update}
   :rotate {:spec ::spec/bot-cmd-default
            :help help-cmd-rotate}
   :assign {:spec ::spec/bot-cmd-assign
            :help help-cmd-assign}
   :who {:spec ::spec/bot-cmd-default
         :help help-cmd-who}
   :shout {:spec ::spec/bot-cmd-default
           :help help-cmd-shout}
   :about {:spec ::spec/bot-cmd-default
           :help help-cmd-about}
   :delete {:spec ::spec/bot-cmd-default
            :help help-cmd-delete}
   :list {:spec ::spec/bot-cmd-list
          :help help-cmd-list}
   :schedule {:spec ::spec/bot-cmd-schedule
              :help help-cmd-schedule}
   :help {:spec ::spec/bot-cmd-help
          :help help-cmd-help}})

;; Helpers

(defn keyword->command
  [kw]
  (if (get commands->data kw) kw nil))

(defn slack-mention-channel
  "Make channel name formatted for Slack API"
  [channel]
  (if (re-matches regex-channel-mention channel)
    channel
    (format "<#%s>" channel)))

(s/fdef get-request-context-with-text
  :args (s/cat :request ::spec/request)
  :ret ::spec/request-context-with-text)

(defn get-request-context-with-text
  [request]
  (let [event (get-in request [:params :event])
        {:keys [text team channel ts]} event
        context {:channel channel
                 :team team
                 :ts ts
                 :text text}
        result
        (into (hash-map) (remove (fn [[_ v]] (nil? v)) context))]
    result))

(defn get-now-ts
  []
  (new java.sql.Timestamp (System/currentTimeMillis)))

;; Parse app mention response

(s/fdef parse-command
  :args (s/cat :raw-text ::spec/raw-text)
  :ret ::spec/command-parsed)

(defn parse-command
  "Parse command from app_mention response.
  Channels, users, and links are expected to be escaped with the angle brakets"
  [raw-text]
  (let [text
        (->>
         raw-text
         helpers/stringify
         helpers/text-trim)
        matcher (re-matcher regex-app-mention text)
        result
        (if (.matches matcher)
          {:command
           (->>
            (.group matcher "command")
            helpers/nilify
            keyword
            keyword->command)
           :rest (->> (.group matcher "rest") helpers/nilify)}
          nil)]
    result))

(defn parse-user-mentions
  "Extract a seq of user-ids mentioned in the string"
  [raw-text]
  (let [text
        (->>
         raw-text
         helpers/stringify
         string/trim)
        users (re-seq regex-user-mention text)
        result (if users (map #(second %) users) nil)]
    result))

;; Parse command arguments

(s/fdef get-command-args
  :args (s/cat :command-parsed ::spec/command-parsed)
  :ret ::spec/command-parsed)

(defn get-command-args
  [command-parsed]
  (->>
   (get command-parsed :rest)
   helpers/stringify
   string/trim))

(s/fdef parse-args
  :args (s/cat :command-parsed ::spec/command-parsed)
  :ret ::spec/args-parsed)

(defmulti parse-args
  "Parse command arguments for app mention"
  (fn [command-parsed] (get command-parsed :command)))

(defn parse-args-create-or-update-cmd
  [command-parsed]
  (let [args (get-command-args command-parsed)
        splitted (string/split args regex-user-mention)
        rotation (->
                  (first splitted)
                  helpers/str-trim
                  helpers/nilify)
        users (parse-user-mentions args)
        ;; without user mentions description will be erroneously
        ;; matched against rota name.
        ;; description without mentions doesn't make any sense
        description (when users (->> (last splitted) helpers/str-trim))]
    {:rotation rotation
     :users users
     :description description}))

(defn parse-args-schedule-cmd
  [command-parsed]
  (let [args (get-command-args command-parsed)
        matcher (re-matcher regex-schedule args)
        result (when (.matches matcher)
                 {:subcommand (.group matcher "subcommand")
                  :executable (.group matcher "executable")
                  :crontab (-> (.group matcher "crontab")
                               helpers/nilify)})]
    result))

(defmethod parse-args :create [command-parsed]
  (parse-args-create-or-update-cmd command-parsed))

(defmethod parse-args :update [command-parsed]
  (parse-args-create-or-update-cmd command-parsed))

(defmethod parse-args :assign [command-parsed]
  (let [args (get-command-args command-parsed)
        splitted (string/split args regex-user-mention)
        rotation (->
                  (first splitted)
                  helpers/str-trim
                  helpers/nilify)
        user (first (parse-user-mentions args))]
    {:rotation rotation
     :user user}))

(defn- parse-args-default
  "Parse arguments for simple commands in the form: command <name>"
  [command-parsed]
  (let [rotation (helpers/nilify (get-command-args command-parsed))
        result (if name {:rotation rotation} nil)]
    result))

(defmethod parse-args :rotate [command-parsed]
  (parse-args-default command-parsed))

(defmethod parse-args :delete [command-parsed]
  (parse-args-default command-parsed))

(defmethod parse-args :who [command-parsed]
  (parse-args-default command-parsed))

(defmethod parse-args :shout [command-parsed]
  (parse-args-default command-parsed))

(defmethod parse-args :about [command-parsed]
  (parse-args-default command-parsed))

(defmethod parse-args :schedule [command-parsed]
  (parse-args-schedule-cmd command-parsed))

(defmethod parse-args :help [_]
  {:description help-msg})

(defmethod parse-args :default [_] nil)

;; Actions

(s/fdef command-exec!
  :args (s/cat :command-map ::spec/command-map)
  :ret ::spec/command-response-text)

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
        rotation (get-in command-map [:args :rotation])]
    {:channel channel :rotation rotation}))

(defmethod command-exec! :who [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        rota (db/duty-get channel rotation)
        duty (get rota :mention/duty)
        description (get rota :rota/description)
        text
        (if duty
          (format
           "Hey %s, you are an on-call person for `%s` rotation.\n%s"
           duty rotation description)
          (format
           "Rotation `%s` not found in channel %s"
           rotation (slack-mention-channel channel)))]
    text))

(defmethod command-exec! :shout [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        rota (db/duty-get channel rotation)
        duty (get rota :mention/duty)
        text
        (or
         duty
         (format
          "Rotation `%s` not found in channel %s"
          rotation (slack-mention-channel channel)))]
    text))

(defmethod command-exec! :about [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        about (db/rota-about-get channel rotation)
        created_on (:created_on about)
        description (:rota/description about)
        users (:users about)
        text
        (if about
          (format
           "Rotation `%s` [%s]: %s.\n%s"
           rotation created_on users description)
          (format
           "Rotation `%s` not found in channel %s"
           rotation (slack-mention-channel channel)))]
    text))

(defmethod command-exec! :delete [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        deleted (db/rota-delete! channel rotation)
        deleted? (> (:next.jdbc/update-count deleted) 0)
        text
        (if deleted?
          (format "Rotation `%s` has been deleted" rotation)
          (format "Rotation `%s` not found in channel %s"
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

(defmethod command-exec! :update [command-map]
  (let [now (get-now-ts)
        {:keys [channel rotation]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        users (get-in command-map [:args :users])
        mentions (users->mention-table-rows users)
        rota-params {:channel channel
                     :name rotation
                     :description (get-in command-map [:args :description])
                     :updated_on now}
        updated (db/rota-update! {:rota rota-params :mention mentions})
        error-msg (get-in updated [:error :message])
        result
        (if (:ok updated)
          (format
           "Rotation `%s` for channel %s updated successfully"
           rotation channel-formatted)
          (do
            (log/error error-msg)
            (format
             "Cannot update rotation `%s` for channel %s: %s"
             rotation channel-formatted error-msg)))]
    result))

(defmethod command-exec! :list [command-map]
  (let [{:keys [channel _]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        rotations (db/rota-list-get channel)
        rota-list
        (->>
         rotations
         (map #(format "- `%s` [%s]" (:rota/name %) (:created_on %)))
         (string/join \newline)
         helpers/nilify)
        text
        (if rota-list
          (format "Rotations created in channel %s:\n%s" channel-formatted rota-list)
          (format "No rotations found in channel %s" channel-formatted))]
    text))

(defmethod command-exec! :rotate [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        {:keys [users-count users-updated prev-duty current-duty]}
        (db/rotate-duty! channel rotation (get-now-ts))
        _ (log/info
           (format "Updated %s/%s for rotation %s of channel %s"
                   users-updated users-count rotation channel))
        duties (map slack/get-user-name [prev-duty current-duty])
        text
        (cond
          (= users-count 0)
          (format "No users found in rotation `%s` of channel %s"
                  rotation channel-formatted)
          (= users-count users-updated)
          (format
           "Users in rotation `%s` of channel %s have been rotated from %s to %s"
           rotation channel-formatted (first duties) (second duties))
          :else
          (format "Failed to rotate users in rotation `%s` of channel %s"
                  rotation channel-formatted))]
    text))

(defmethod command-exec! :assign [command-map]
  (let [{:keys [channel rotation]} (get-channel-rotation command-map)
        channel-formatted (slack-mention-channel channel)
        name (get-in command-map [:args :user])
        assigned (db/assign! channel rotation name (get-now-ts))
        text
        (if (= assigned :user-not-found)
          (format "User %s is not found in rotation `%s` of channel %s"
                  name rotation channel-formatted)
          (format "Assigned user %s in rotation `%s` of channel %s"
                  name rotation channel-formatted))]
    text))

(defn schedule-args-valid?
  [command-map]
  (let [{:keys [subcommand executable crontab]} (get command-map :args)
        subcommand-ok? (contains? #{"create" "delete" "list"} subcommand)
        executable-ok? (or (= subcommand "list")
                           (->> executable
                                (format "<@placeholder> %s")
                                parse-command
                                :command
                                some?))
        crontab-ok? (or (contains? #{"delete" "list"} subcommand)
                        (-> crontab
                            kairos/parse-cron
                            some?))]
    (and subcommand-ok?
         executable-ok?
         crontab-ok?)))

(defn get-run-at
  "Return java.sql.Timestamp for the next run for a given crontab string"
  [crontab]
  (try (-> crontab
           (kairos/get-dt-seq)
           first
           .toInstant
           java.sql.Timestamp/from)
       (catch Exception _ nil)))

(defmethod command-exec! :schedule [command-map]
  (if (schedule-args-valid? command-map)
    (let [crontab (get-in command-map [:args :crontab])
          query-params {:channel (get-in command-map [:context :channel])
                        :executable (get-in command-map [:args :executable])
                        :crontab crontab
                        :run_at (get-run-at crontab)}
          {:keys [result error]}
          (case (keyword (get-in command-map [:args :subcommand]))
            :create (db/schedule-insert! query-params)
            :delete (db/schedule-delete! query-params)
            :list (db/schedule-list query-params))
          message (or result (:message error))]
      message)
    (format "Invalid arguments for `schedule` command: %s" (:args command-map))))

(defn get-help-message []
  (let [version (get-in config [:application :version])]
    (format
     help-intro
     version
     help-msg)))

(defmethod command-exec! :default [_]
  (get-help-message))

;; Entry point

(s/fdef get-command-map
  :args (s/cat :request ::spec/request)
  :ret ::spec/command-map)

(defn get-command-map
  "Get parsed command map from app_mention request"
  [request]
  (let [request-context-with-text (get-request-context-with-text request)
        text (:text request-context-with-text)
        context (dissoc request-context-with-text :text)
        parsed-command (parse-command text)
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

(s/fdef get-command-response
  :args (s/cat :request ::spec/request)
  :ret ::spec/command-response)

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

(s/fdef send-command-response!
  :args (s/cat :request ::spec/request)
  :ret ::spec/command-response)

(defn send-command-response!
  [request]
  (let [body-map (get-command-response request)
        body (json/generate-string body-map)
        {:keys [status data]}
        (slack/slack-api-request {:method :chat.postMessage :body body})
        _ (log/info
           (format "Post message to Slack: status %s body %s" status data))]
    body-map))
