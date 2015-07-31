(ns netrunner.chat
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [sablono.core :as sab :include-macros true]
            [cljs.core.async :refer [chan put! <!] :as async]
            [netrunner.auth :refer [avatar authenticated] :as auth]
            [netrunner.ajax :refer [GET]]
            [netrunner.main :as main]))

(def app-state
  (atom {:channels {:general [] :america [] :europe [] :asia-pacific [] :francais [] :italia [] :sverige []}}))

(def chat-channel (chan))
(def chat-socket (.connect js/io (str js/iourl "/chat")))

(.on chat-socket "netrunner" #(put! chat-channel (js->clj % :keywordize-keys true)))

(go (while true
      (let [msg (<! chat-channel)
            ch (keyword (:channel msg))
            messages (get-in @app-state [:channels ch])]
        (swap! app-state assoc-in [:channels ch] (conj messages msg)))))

(defn send-msg [event channel owner]
  (.preventDefault event)
  (authenticated
   (fn [user]
     (let [input (om/get-node owner "msg-input")
           text (.-value input)]
       (when-not (empty? text)
         (.emit chat-socket "netrunner" #js {:channel (name channel)
                                             :msg text
                                             :username (:username user)
                                             :emailhash (:emailhash user)})
         (aset input "value" "")
         (.focus input))))))

(defn msg-input-view [{:keys [channel]} owner]
  (om/component
   (sab/html
    [:form.msg-box {:on-submit #(send-msg % channel owner)}
     [:input {:type "text" :ref "msg-input" :placeholder "Say something..." :accessKey "l"}]
     [:button "Send"]])))

(defn channel-view [{:keys [channel active-channel]} owner]
  (reify
    om/IRenderState
    (render-state [this state]
      (sab/html
       [:div.block-link {:class (if (= active-channel channel) "active" "")
                         :on-click #(put! (:channel-ch state) channel)}
        (str "#" (name channel))]))))

(defn message-view [message owner]
  (om/component
   (sab/html
    [:div.message
     (om/build avatar message {:opts {:size 38}})
     [:div.content
      [:div
       [:span.username (:username message)]
       [:span.date (-> (:date message) js/Date. js/moment (.format "dddd MMM Do - HH:mm"))]]
      [:div (:msg message)]]])))

(defn fetch-messages [owner]
  (let [channel (om/get-state owner :channel)
        messages (get-in @app-state [:channels channel])]
    (when (empty? messages)
      (go (let [data (:json (<! (GET (str "/messages/" (name channel)))))]
            (swap! app-state assoc-in [:channels channel] data))))))

(defn chat [cursor owner]
  (reify
    om/IInitState
    (init-state [this] {:channel :general :channel-ch (chan)})

    om/IWillMount
    (will-mount [this]
      (fetch-messages owner)
      (go (while true
            (let [ch (<! (om/get-state owner :channel-ch))]
              (om/set-state! owner :channel ch)))))

    om/IDidUpdate
    (did-update [this prev-props prev-state]
      (fetch-messages owner)
      (let [div (om/get-node owner "message-list")]
        (aset div "scrollTop" (.-scrollHeight div))))

    om/IRenderState
    (render-state [this state]
      (sab/html
       [:div.chat-app
        [:div.blue-shade.panel.channel-list
         [:h4 "Channels"]
         (for [ch (keys (:channels cursor))]
           (om/build channel-view {:channel ch :active-channel (:channel state)}
                     {:init-state {:channel-ch (:channel-ch state)}}))]
        [:div.chat-box
         [:div.blue-shade.panel.message-list {:ref "message-list"}
          (om/build-all message-view (get-in cursor [:channels (:channel state)]))]
         [:div
          (om/build msg-input-view state)]]]))))

(om/root chat app-state {:target (. js/document (getElementById "chat"))})
