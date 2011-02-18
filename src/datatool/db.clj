;;ref http://wiki.sproutcore.com/Todos+06-Building+with+Compojure+and+MongoDB

(ns datatool.db
  (:require [somnium.congomongo :as m])
  (:require [clj-time.core :as t])
  (:require [clj-time.coerce :as c])
  (:require [clojure.contrib.string])
  (:require [datatool.util :as u])
  (:require [clojure.contrib.str-utils2 :as s]))

(m/mongo! :db "data" :host "127.0.0.1")


(declare keywordify-keys)
(declare merge-with-kw-keys)

;execute f against args in this namespace
(defmacro exec [f & args] `(~f ~@args))

(defn- uuid []
  (str (java.util.UUID/randomUUID)))


(defn- endow [mongo-item]
  (assoc mongo-item :_id (uuid) :updated_at (c/to-long (t/now))))

;replace Mongo's ids with Java UUIDs and add 'item' into the specified 'collection'
(defn add-item! [collection mongo-item]
    (dosync
      (m/insert! collection (endow mongo-item))
      (m/insert! :tags (endow {:name "tag"}))))


;;options is a list, e.g. (:datatypes :sort {:tags 1} :skip 1 :limit 1)
(defn fetch-with-options [options]
  (apply m/fetch options))

;returns: one mongo "datatypes" object, in the form of a clojure map (clojure.lang.PersistentArrayMap)
(defn search-by-datatype-name [name]
       (m/fetch-one :datatypes :where {:datatype_name name}))

(defn search-by-datatype-id [id]
  (m/fetch-one :datatypes :where {:_id (str id)})) 

(defn get-elements [coll]
  (m/fetch coll))

(defn get-coll-count [coll]
  (count (m/fetch coll))) 


;returns field names as list: ("field_name1" "field_name2")
(defn get-column-names [^java.lang.String datatype_id]
  (let [mongo-datatype (m/fetch-one :datatypes :where {:_id datatype_id})]
    (map #(% :name) (mongo-datatype :fields))))

;fetch column names for datatype, given datatype_id
;result format: "url1 url2"
(defn get-column-names-as-string [^java.lang.String datatype_id]
  (let [mongo-datatype (m/fetch-one :datatypes :where {:_id datatype_id})]
    (u/compact-str-list (get-column-names datatype_id)) ))


;return symbol, e.g. :abc
(defn get-data-mongo-coll-name-for [datatype_id]
  ((m/fetch-one :datatypes :where {:_id datatype_id}) :datatype_name))


;datatype-fe is front-end format item (clojure map)
(defn get-mongo-hash-datatype [datatype-fe]
  (let [
	  coll
	  (->>
	  datatype-fe ;dataype-fe: {"name.0" "url", "name.1" "url2", "datatype_name" "the_name_of_the_datatype", "datatype_tags" "trading generic", "comments_area", "..."}
    (filter #(.contains (str (key %)) "name."))  ;(["name.0" "url0"] ["name.1" "url1"])
    (map #(str (get % 1)))  ;("url0" "url1") 
	  (map (fn [x] {:name x :type "String"} ))) ;;takes care of mapping the fields into {:name field_name :type field_type}
	  ; ({"name" "url0", "type" "String"} {"name" "url1", "type" "String"})
	  tags (into [] (.split (datatype-fe "datatype_tags") " "))
	  ;tags: ["tag1" "tag2"]
	  mongo-hash {:datatype_name (datatype-fe "datatype_name"), :tags tags, :fields (into [] coll), :comments (datatype-fe "comments_area")}
	  ; mongo_hash: {:datatype_name "<some name>", :tags ["tag1" "tag2"],:fields [{:name "url", :type "String"} {:name "url2", :type "String"}], :comments "..." }
	] mongo-hash))

;datum-fe: {"_id" "", "label.2" "hello", "name.2" "b", "label.1" "some", "name.1" "a", "type" "data", "tags" "tag1 tag2", "datatype_name" "abcdefg"}
(defn get-mongo-hash-datum [datum-fe]
  (let [tags (into [] (.split (datum-fe "tags") " "))
        id (if (nil? (datum-fe :_id)) "" (datum-fe :_id)) 
        names-coll
        (->>
          datum-fe
          (filter #(.contains (str (key %)) "name."))
          (map #(str (get % 1)))
          (map (fn[x] {:value x :type "String"})))   ;({"name" "url0", "type" "String"} {"name" "url1", "type" "String"}) 
        labels-coll
        (->>
          datum-fe
          (filter #(.contains (str (key %)) "label.")))    ;(["label.0" "val0"] ["label.1" "val1"])      
        fields-coll
          (zipmap names-coll labels-coll)     ;{{:value "a", :type "String"} ["label.1" "some"], {:value "b", :type "String"} ["label.2" "hello"]}
        all-fields
        (for [[a b] fields-coll] (assoc a (first b) (first (rest b))) ) 
        ;all-fields: ({"label.1" "some", :value "a", :type "String"} {"label.2" "hello", :value "b", :type "String"})
        final-fields (reduce (fn[x y] (merge x { (val (first (filter (fn[i] (.contains (str (key i)) "label.")) y))) (val (first (filter (fn [i] (.contains (str (key i)) "value")) y))) }) ) {} all-fields) 
        ;;final-fields: (it basically gets rid completely of the fields array and types; types can be obtained from the datatype object) {"hello" "b", "some" "a"}  
        ] 
        ;return this
        (merge {:tags tags, :_id id} final-fields) ))

(defmulti get-mongo-hash 
  (fn[x] (x "type")))

(defmethod get-mongo-hash "datatype" [fe]
  (get-mongo-hash-datatype fe))

(defmethod get-mongo-hash :default [fe]
  (get-mongo-hash-datum fe))


(defn update-datatype-with-fe-item! [datatype-fe]
  (let [fe-item-mongo-hash (get-mongo-hash datatype-fe)
	  existing-mongo-item (search-by-datatype-name (datatype-fe :datatype_name))]
      (if (nil? existing-mongo-item) 
        (add-item! type fe-item-mongo-hash)
        ;else:
        (m/update! type existing-mongo-item (merge existing-mongo-item fe-item-mongo-hash)))))
  
  
(defn update-datum-with-fe-item! [datum-fe]
  (let [mongo-hash (get-mongo-hash datum-fe)]
    (if (nil? (datum-fe :_id))
      (add-item! (datum-fe "type") mongo-hash) ;puts it into its own collection designated by the type field
      (m/update! (datum-fe "type") mongo-hash  (merge (m/fetch (datum-fe "type") :where {:_id (datum-fe :_id)}) mongo-hash)   )))) ;untested yet

;;id is string, type is exactly the collection name, e.g. :datatypes
(defn delete-by-id! [type id]
  (let [mongo-item (m/fetch-one type :where {:_id id})]
    (m/destroy! type mongo-item)))

(defn drop-coll [coll]
  (m/drop-coll! coll))


(defn keywordify-keys
  "Returns a map otherwise same as the argument but with all keys turned to keywords"
  [m]
  (zipmap
   (map keyword (keys m))
   (vals m)))

;note: we use reduce because "maps" is as array of maps [ {...} {..} {.....} ]
(defn merge-with-kw-keys
  "Merges maps converting all keys to keywords"
  [& maps]
  (reduce
   merge
   (map keywordify-keys maps)))

       
	


    
