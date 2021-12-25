(ns dienstplan.commands
  (:gen-class)
  (:require
   [dienstplan.spec :as spec]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]))

;; Const

(def help-msg
  "To start interacting with the bot, mention its username, provide a command and its arguments as follows:

@dienstplan <command> [<options>]

Commands:

1. Create a rotation
@dienstplan create <name> <list of users> <description>

2. Rotate: take the next user on the rotation list
@dienstplan rotate <name>

3. Show who is duty
@dienstplan show <name>

4. Delete a rotation
@dienstplan delete <name>

5. Show help message
@dienstplan help

Example:

Let's create a rotation with dienstplan:

@dienstplan create backend-rota @user1 @user2 @user3
On-call backend engineer's duty:
- Check support questions
- Check alerts
- Check metrics

Now let's use Slack reminder to rotate weekly:

/remind @channel @dienstplan rotate backend-rota every Monday at 9AM UTC

Let's also show current duty engineer with a reminder:

/remind @channel @dienstplan show backend-rota every Tuesday, Wednesday, Thursday, Friday at 9AM UTC")

(def help-cmd-create
  "Usage:
@dienstplan create <name> <list of users> <description>

Example:
@dienstplan create backend-rota @user1 @user2 @user3
On-call backend engineer's duty:
- Check support questions
- Check alerts
- Check metrics")

(def help-cmd-rotate
  "Usage:
@dienstplan rotate <name>

Example:
@dienstplan rotate backend-rota")

(def help-cmd-show
  "Usage:
@dienstplan show <name>

Example:
@dienstplan show backend-rota")

(def help-cmd-delete
  "Usage:
@dienstplan delete <name>

Example:
@dienstplan delete backend-rota")

(def help-cmd-help
  "Usage:
@dienstplan help")

(def regex-user-mention #"(?s)\<@(?<userid>[A-Z0-9]+)\>")

(def regex-app-mention
  "(?s) is a pattern flag for dot matching all symbols including newlines"
  #"(?s)\<@(?<userid>[A-Z0-9]+)\>\s+(?<command>\w+)\s*(?<rest>.*)")

(def commands->data
  {:create {:spec ::spec/bot-cmd-create
            :help help-cmd-create}
   :rotate {:spec ::spec/bot-cmd-default
            :help help-cmd-rotate}
   :delete {:spec ::spec/bot-cmd-default
            :help help-cmd-delete}
   :show {:spec ::spec/bot-cmd-default
          :help help-cmd-show}
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

(defn get-event-app-mention
  [request]
  (let [event (get-in request [:params :event])
        {:keys [text team channel ts]} event]
    {:channel channel
     :team team
     :ts ts
     :text text}))

;; Parse app mention response

(defn parse-app-mention
  "Parse text from app_mention response.
  Channels, users, and links are expected to be escaped with the angle brakets"
  [raw-text]
  (let [text
        (->>
         raw-text
         stringify
         str/trim)
        matcher (re-matcher regex-app-mention text)
        result
        (if (.matches matcher)
          {:user-id (->> (.group matcher "userid") nilify)
           :command
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
        name (->> (first splitted) str/trim)
        description (->> (last splitted) str/trim)
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

(defmethod parse-args :show [app-mention]
  (parse-args-default app-mention))

(defmethod parse-args :help [_]
  {:description help-msg})

(defmethod parse-args :default [_] nil)

;; Actions

(defmulti command-exec
  "Execute the command"
  (fn [command-map] (:command command-map)))

(defmethod command-exec :default [command-map]
  ;; TODO
  (str "Parsed command: " command-map))

;; Entry point

(defn get-command
  "Get parsed command map from app_mention request"
  [request]
  (let [{:keys [text]} (get-event-app-mention request)
        parsed-command (parse-app-mention text)
        {:keys [user-id command]} parsed-command
        {:keys [spec help]} (get commands->data command)
        args (parse-args parsed-command)
        command-map (if parsed-command
                  {:user-id user-id
                   :command command
                   :args args}
                  nil)
        result (cond
                 (nil? spec) {:error help-msg}
                 (s/valid? spec command-map) command-map
                 :else (assoc command-map :error help))]
    result))

(defn get-response
  "Get response for app mention request"
  [request]
  (let [command-map (get-command request)
        error (:error command-map)
        text (or error (command-exec (:command command-map)))
        response {:channel "1" ;; FIXME
                  :text text}]
    response))
