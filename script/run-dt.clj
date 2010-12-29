(use 'ring.adapter.jetty)
(use 'datatool.api)

(let [port (Integer/parseInt (get (System/getenv) "PORT" "81"))]
    (run-jetty #'datatool.api/app {:port port}))
