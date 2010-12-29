(ns datatool.api
  (:use compojure.core)  
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use [ring.middleware reload stacktrace]) ;;replaced with the ones from compojure (see below) file file-info])
  (:require [datatool.db :as db])
  (:require [somnium.congomongo :as congo]) ;temporary, until I figure out
  (:require [datatool.util :as u])
  (:use clojure.contrib.json)
  
  ;clj-time
  (:require [clj-time.core]) 
  (:require [clj-time.coerce]) 
  (:require [clj-time.format])

  ;http://formpluslogic.blogspot.com/2010/08/securing-web-applications-with-sandbar.html
  (:use sandbar.stateful-session)
  (:use sandbar.auth)

  (:require [compojure.route :as route])
  (:use [clojure.java.io :only (file)]))

;;references:
;;http://wiki.sproutcore.com/Todos+06-Building+with+Compojure+and+MongoDB
;;http://mmcgrana.github.com/2010/08/clojure-rest-api.html


(defn- emit-json
  "Turn the object to JSON, and emit it with the correct content type. x is seq"
  [x]
  {:headers {"Content-Type" "application/json"}
   :body (json-str x)})

(defn datatype-path
  "Returns the relative URL for datatype"
  [datatype]
  (str "/datatype/" (:_id datatype)))

(defn data-path
   "Returns the relative URL for datum"
  [datum]
  (str "/data/" (:_id datum)))

(defn view-layout [body-content & header-content] ;[& content]
  (html
   (doctype :xhtml-strict)
   (xhtml-tag "en"
        [:head
         [:meta {:http-equiv "Content-type"
           :content "text/html; charset=utf-8"}]
         (include-js "/js/jquery-1.4.3.min.js" "/js/plugins/jquery-autocomplete/lib/jquery.bgiframe.min.js" "/js/jquery.autocomplete.min.js")
         (include-css "/css/jquery.autocomplete.css")
         header-content

         [:title "Datatool"]
         ]
        [:body 
          [:div {:id "header-main"}
            (slurp "public/header.html")
          ]
          [:p]
          [:p]
          [:div {:id "main-content"}
            body-content
          ]
        ])))
 

(defn simple-view-layout [content & js-includes]
  (html
   (doctype :xhtml-strict)
   (xhtml-tag "en"
        [:head
         [:meta {:http-equiv "Content-type"
          :content "text/html; charset=utf-8"}]
          (let [js '("/js/jquery-1.4.3.min.js" "/js/plugins/jquery-autocomplete/lib/jquery.bgiframe.min.js" "/js/jquery.autocomplete.min.js")
                js (concat js js-includes)]
            (apply include-js js))
          (include-css "/css/jquery.autocomplete.css")
    [:title "Datatool"]]
    [:body
     [:div {:id "main"}
            content
      ]])))

(defn datatype-added-view-output []
  "Datatype addition request posted")

(defn view-output-nice-list [datatypes]
  (view-layout
    (for [d datatypes]
      [:p
       (str d)
       [:a {:href (str "/datatypes/delete/" (d :_id)) } " Delete" ]
       ])))

;transform data item from db representation into front-end representation
(defmulti db-to-fe
    (fn[x] (x :type)))

(defmethod db-to-fe "datatype" [db-datatype] 
  (let [d (dissoc db-datatype :type)
        fields (into [] (map #(% :name) (d :fields))) ]
    (assoc d :fields fields)))

;;routes
(defroutes handler

  ;(datum-form (fn [request form] (view-layout form)))

  (route/files "/"
   {:root "/home/victor/dt/public"}
  (route/not-found
    (file "/home/victor/dt/pages/404.html")))

  (GET "/datatypes/edit/:id" {params :params}
      (spit "public/js/edit_datatype.js" (str "var datatype=" (json-str (db-to-fe (assoc (db/search-by-datatype-id (params "id")) :type "datatype")) ) ";") )
      (simple-view-layout (slurp "public/datatype-edit.html") "/js/edit_datatype.js"))

  (GET "/datatypes/new" []
       (simple-view-layout (slurp "public/datatype-edit.html")))


  (GET "/data/edit" []
       (slurp "public/datum-edit.html"))
  (GET "/myfirstgrid" []
       (slurp "public/myfirstgrid.html"))

  (GET "/datatypes/grid" []
       (slurp "public/datatypes.html"))

  (GET "/data/grid/:id" {params :params}
     (spit "public/js/id.js" (str "var datatype_id = '" (params "id") "';" ))
     (slurp "public/data.html"))
  
  (POST "/data/json-for-grid-setup" {params :params}
      (emit-json {"colNames" (db/get-column-names-as-string (params "id")) })) ; {"colNames" "col_name1 col_name2 tags updated_at"}



  ;Returns table grid Json representation
  ;TODO: rename this to "/datatypes" and do the choosing based on the header types
  (POST "/datatypes/json-for-grid" {params :params}
  ;;Note: params is: {"sord" "desc", "sidx" "id", "page" "1", "rows" "10", "nd" "1289512312346", "_search" "false"}
    (let [rowsPerPage (Integer. (params "rows")) ;;rowNum: how many rows we want to have in the grid 
          reqPageNum (Integer. (params "page")) ;;page requested
          sord (if (= (params "sord") "asc") 1 -1) ;;get the sorting direction
          countAll (count (congo/fetch :datatypes)) ;;TODO: this is absolutely stupid, find a better way of getting the count! 
          recordsForPage (db/fetch-with-options ;;(params "sidx") sord
                           [:datatypes :sort {(params "sidx") sord} :skip (* (- reqPageNum 1) rowsPerPage) :limit rowsPerPage ])
          totalPages (java.lang.Math/ceil (/ countAll rowsPerPage))
          
      ]

      ;;turn every element of the map {:a "b", :c "d"} from a mapentry to a map in itself, then put that into a vectory with "into []"
      ;;(into [] (map (partial conj {}) {:a "b", :c "d"})) -->  [{:a "b"} {:c "d"}], this is passed to the "rows" below as an array.
      (emit-json
        {"page" reqPageNum, "records" rowsPerPage, "total" totalPages, 
         "rows" ;;datatype_name,tags,fields,comments - TODO: truncate comments & fields as needed
          (into [] (map (partial conj {}) 
           ;;:tags ["tag1" "tag2"],:fields [{:name "url", :type "String"} {:name "url2", :type "String"}]   -- (r :comments)
            (for [r recordsForPage]  {"id" (r :_id), "cell" 
                                      [(r :datatype_name) (r :tags) (into [] (for [f (r :fields)]  (:name f)))
                                       (r :comments) (u/date-to-string (r :updated_at)) ]}) ))
          })))


  ;Returns table grid Json representation
  ;TODO: rename this to "/data" and do the choosing based on the header types
  (POST "/data/json-for-grid/:datatype_id" {params :params}
  ;;Note: params is: {"sord" "desc", "sidx" "id", "page" "1", "rows" "10", "nd" "1289512312346", "_search" "false"}
    (let [data-mongo-coll-name (db/get-data-mongo-coll-name-for (params "datatype_id")) ;returns e.g. :abc
          rowsPerPage (Integer. (params "rows")) ;;rowNum: how many rows we want to have in the grid 
          reqPageNum (Integer. (params "page")) ;;page requested
          sord (if (= (params "sord") "asc") 1 -1) ;;get the sorting direction
          countAll (count (congo/fetch data-mongo-coll-name)) ;*** ;TODO: this is absolutely stupid, find a better way of getting the count! 
          recordsForPage (db/fetch-with-options 
                           [data-mongo-coll-name :sort {(params "sidx") sord} :skip (* (- reqPageNum 1) rowsPerPage) :limit rowsPerPage ])
          totalPages (java.lang.Math/ceil (/ countAll rowsPerPage)) ]

      ;;turn every element of the map {:a "b", :c "d"} from a mapentry to a map in itself, then put that into a vectory with "into []"
      ;;(into [] (map (partial conj {}) {:a "b", :c "d"})) -->  [{:a "b"} {:c "d"}], this is passed to the "rows" below as an array.
      (emit-json
        {"page" reqPageNum, "records" rowsPerPage, "total" totalPages, 
         "rows" ;tags,fields
          (into [] (map (partial conj {}) 
           ;;:tags ["tag1" "tag2"],:fields [{:name "url", :type "String"} {:name "url2", :type "String"}]   -- (r :comments)
            (for [r recordsForPage]  {"id" (r :_id), "cell" ;(into [] (for [f (r :fields)]  (:name f)))
                                       (conj (into [] (for [i (db/get-column-names (params "datatype_id"))] (r (keyword i)) )) 
                                      (r :tags) (u/date-to-string (r :updated_at)) ) ;TODO: refactor so it will be clear about column order and tags & updated_at 
                                      }) )) })))


  (GET "/datatypes/gen/:number" {params :params}
       (apply str (u/generateSampleDatatypes (Integer. (params "number")))))

  
  (GET "/datatypes" []
       (emit-json  (congo/fetch :datatypes)))

  (GET "/data" []
      (emit-json  (congo/fetch :datatypes)))

     
  (GET "/datatypes/search-by-name" {params :params}
        (emit-json (db/search-by-datatype-name (params "name_value"))))
       

  (GET "/datatypes/names" []
       (emit-json
	      (let [all (map :datatype_name (congo/fetch :datatypes))]
	           (distinct (filter #(not (or (= % "") (= % nil))) all)))))

  
  ;;TODO: look up how to determine request's expected content type - multi-format REST API
  (GET "/my-datatypes" []
       (view-output-nice-list (congo/fetch :datatypes)))

  ;;TODO: look up how to determine request's expected content type - multi-format REST API
  (GET "/my-data/:type" {params :params}
       (view-output-nice-list (congo/fetch (params "type"))))

  ;e.g. params = {"comments_area" "acdc", "name.1" "lac", "type" "datatype", "datatype_tags" "abc-topic", "datatype_name" "abc"} 
  (POST "/datatypes" {params :params}
	  (let [saved-datatype (db/update-datatype-with-fe-item! params)]
	    {:status 201
	    :headers {"Location" (datatype-path saved-datatype)}
	    :body (str "Left as " params " Returned: " saved-datatype)}
	))
 
  (POST "/data" {params :params}
	  (let [saved-data (db/update-datum-with-fe-item! params)]
	    {:status 201
	    :headers {"Location" (data-path saved-data)}
	    :body (str "Left as " params " Returned: " saved-data)} ))
  
  (GET "/datatypes/delete" {}
       (congo/drop-coll! :datatypes)
       "Datatype collection was cleared.")


   (GET "/datatypes/delete/:id" {params :params}
       (db/delete-by-id! :datatypes (params "id")))


  (GET "/" []
       (view-layout "")) 
       

)

(def app
  (-> #'handler
      (with-security authenticate)
      wrap-stateful-session
      (wrap-reload '[datatool.api])
      (wrap-reload '[datatool.db])
      (wrap-reload '[datatool.util])
      (wrap-stacktrace)
      ))
