(ns datatool.api
  (:use compojure.core)  
  (:use hiccup.core)
  (:use hiccup.page-helpers)
  (:use [ring.middleware reload stacktrace]) 
  (:require [datatool.db :as db])
  (:require [datatool.util :as u])
  (:use clojure.contrib.json)
  
  ;clj-time
  (:require [clj-time.core]) 
  (:require [clj-time.coerce]) 
  (:require [clj-time.format])

  (:require [compojure.route :as route])
  (:use [clojure.java.io :only (file)]))


(def APP-PATH "/home/victor/dt")

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


;transform data item from db representation into front-end representation
(defmulti db-to-fe
    (fn[x] (x :type)))

(defmethod db-to-fe "datatype" [db-datatype] 
  (let [d (dissoc db-datatype :type)
        fields (into [] (map #(% :name) (d :fields))) ]
    (assoc d :fields fields)))

;;routes
(defroutes handler

  (route/files "/"
   {:root (str APP-PATH "/public")}
  (route/not-found
    (file (str APP-PATH "/pages/404.html"))))

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


  ;Returns table grid JSON representation
  ;Note: params is: {"sord" "desc", "sidx" "id", "page" "1", "rows" "10", "nd" "1289512312346", "_search" "false"}
  
  (POST "/datatypes/json-for-grid" {params :params}
    (let [rowsPerPage (Integer. (params "rows")) ;;rowNum: how many rows we want to have in the grid 
          reqPageNum (Integer. (params "page")) ;;page requested
          sord (if (= (params "sord") "asc") 1 -1) ;;get the sorting direction
          countAll (db/get-coll-count :datatypes) 
          recordsForPage (db/fetch-with-options 
                           [:datatypes :sort {(params "sidx") sord} :skip (* (- reqPageNum 1) rowsPerPage) :limit rowsPerPage ])
          totalPages (java.lang.Math/ceil (/ countAll rowsPerPage))
          
      ]

      ;;turn every element of the map {:a "b", :c "d"} from a mapentry to a map in itself, then put that into a vectory with "into []"
      (emit-json
        {"page" reqPageNum, "records" rowsPerPage, "total" totalPages, 
         "rows" 
          (into [] (map (partial conj {}) 
            (for [r recordsForPage]  {"id" (r :_id), "cell" 
                                      [(r :datatype_name) (r :tags) (into [] (for [f (r :fields)]  (:name f)))
                                       (r :comments) (u/date-to-string (r :updated_at)) ]}) ))
          })))


  ;Returns table grid Json representation
  ;Note: params is: {"sord" "desc", "sidx" "id", "page" "1", "rows" "10", "nd" "1289512312346", "_search" "false"}
           
  (POST "/data/json-for-grid/:datatype_id" {params :params}
   (let [data-mongo-coll-name (db/get-data-mongo-coll-name-for (params "datatype_id")) ;returns e.g. :abc
          rowsPerPage (Integer. (params "rows")) ;;rowNum: how many rows we want to have in the grid 
          reqPageNum (Integer. (params "page")) ;;page requested
          sord (if (= (params "sord") "asc") 1 -1) ;;get the sorting direction
          countAll (db/get-coll-count data-mongo-coll-name) 
          recordsForPage (db/fetch-with-options 
                           [data-mongo-coll-name :sort {(params "sidx") sord} :skip (* (- reqPageNum 1) rowsPerPage) :limit rowsPerPage ])
          totalPages (java.lang.Math/ceil (/ countAll rowsPerPage)) ]

      ;;turn every element of the map {:a "b", :c "d"} from a mapentry to a map in itself, then put that into a vectory with "into []"
      (emit-json
        {"page" reqPageNum, "records" rowsPerPage, "total" totalPages, 
         "rows" ;tags,fields
          (into [] (map (partial conj {}) 
           ;;:tags ["tag1" "tag2"],:fields [{:name "url", :type "String"} {:name "url2", :type "String"}]   -- (r :comments)
            (for [r recordsForPage]  {"id" (r :_id), "cell" ;(into [] (for [f (r :fields)]  (:name f)))
                                       (conj (into [] (for [i (db/get-column-names (params "datatype_id"))] (r (keyword i)) )) 
                                      (r :tags) (u/date-to-string (r :updated_at)) ) 
                                      }) )) })))


  ;generate testing samples         
  (GET "/datatypes/gen/:number" {params :params}
       (apply str (u/generateSampleDatatypes (Integer. (params "number")))))

  
  (GET "/datatypes" []
       (emit-json  (db/get-elements :datatypes)))

     
  (GET "/datatypes/search-by-name" {params :params}
        (emit-json (db/search-by-datatype-name (params "name_value"))))
       

  (GET "/datatypes/names" []
       (emit-json
	      (let [all (map :datatype_name (db/get-elements :datatypes))]
	           (distinct (filter #(not (or (= % "") (= % nil))) all)))))

  
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
	    :body "Datatype saved" } ))
  
  (GET "/datatypes/delete" {}
       (db/drop-coll :datatypes)
       "Datatype collection was cleared.")


   (GET "/datatypes/delete/:id" {params :params}
       (db/delete-by-id! :datatypes (params "id")))


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
