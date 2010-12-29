(ns datatool.core
  (:use compojure.core)
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use [ring.middleware reload stacktrace file file-info])
  (:use datatool.api)
  (:use datatool.db))

;;(defn view-layout [& content]
;;  (html
;;   (doctype :xhtml-strict)
;;   (xhtml-tag "en"
;;	      [:head
;;	       [:meta {:http-equiv "Content-type"
;;		       :content "text/html; charset=utf-8"}]
;;	       [:title "Datum"]]
;;	      [:body content])))





(defn view-layout [& content]
  (html
   (doctype :xhtml-strict)
   (xhtml-tag "en"
	      [:head

	       [:meta {:http-equiv "Content-type"
		       :content "text/html; charset=utf-8"}]
	       [:title "Datum"]]
	                    [:body content]))) 



(defn view-input []
  (view-layout
   [:h2 "Enter one datum:"]
   [:form {:method "post" :action "/"}
    [:input.datum {:type "text" :name "my_datum"}]		   
    [:input.action {:type "submit" :value "Add"}]]))


;;(defn view-output [a]
;;  (view-layout
;;   [:h2 (str "This data is being returned: " (str a))]))
  

;;(defn view-output
;;  (view-layout
 ;;  [:h2 (str "csdacsdc ")]));;calls with empty vector

;(defn view-output [udseless]
  ;;"hello guys"
;  view-layout ["nothing"]
;  )

(defroutes handler

  ;;(GET "/" []
  ;;     (view-output))

  (GET "/" []
       (find-all-data))
       ;;view-output "!useless");;wrong: note: this has a requirement of passing one arg to the func -- this should not be--to email about this


  (GET "/datatype/new" []
       (slurp "/var/www/datatype-edit.html"))


  (POST "/datatypes/save" {params :params}
      ;;get the params
       (view-layout (str "params: " params ) )) 


       
  ;;(GET "/get-data" []
  ;;     (view-output (find-all-data))) ;;find all using direct call


  ;;how to make a CURL request to http://184.106.223.62:81/data
  ;;and then turn response into a string to display
  ;;OR simply create a method in datatool.api that would return the json and have its GET also use it

  ;;next: display simple forms (and views)

  ;;(POST "/" {params :params}
  ;;	(view-output (params "my_datum")))
  )

(def app
  (-> #'handler
      (wrap-reload '[datatool.core])
      (wrap-stacktrace)
      (wrap-file "Documents")
      (wrap-file-info)))
