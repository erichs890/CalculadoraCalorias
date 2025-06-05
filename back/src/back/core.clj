(ns back.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [cheshire.core :as json]
            [clj-http.client :as http])
  (:import (java.time LocalDate)
           (java.net URLEncoder)))

;; ATOMOS SEPARADOS
(defonce usuarios (atom nil))
(defonce alimentos (atom []))
(defonce atividades (atom []))

;; FUNÇÕES AUXILIARES
(defn parse-data [s]
  (try (LocalDate/parse s) (catch Exception _ nil)))

(defn dentro-do-periodo? [data-str inicio fim]
  (let [data (parse-data data-str)]
    (and (not (.isBefore data inicio))
         (not (.isAfter data fim)))))

;; ROTAS
(defroutes rotas
           (GET "/" [] "Servidor de Calorias Ativo.")

           ;; USUÁRIO
           (POST "/usuario" req
             (let [body (-> req :body slurp (json/parse-string true))]
               (reset! usuarios body)
               {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:mensagem "Usuário cadastrado com sucesso."})}))

           (GET "/usuario" []
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string @usuarios)})

           ;; ALIMENTO
           (POST "/alimento" req
             (let [desc (:descricao (-> req :body slurp (json/parse-string true)))
                   url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao="
                            (URLEncoder/encode desc "UTF-8"))]
               (try
                 (let [res   (http/get url {:as :json})
                       item  (first (:body res))
                       cal   (:calorias item)]
                   (if cal
                     (let [trans {:tipo "ganho" :descricao desc :calorias cal :data (str (LocalDate/now))}]
                       (swap! alimentos conj trans)
                       {:status 200 :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:mensagem "Alimento registrado." :transacao trans})})
                     {:status 404 :headers {"Content-Type" "application/json"}
                      :body (json/generate-string {:erro "Alimento não encontrado"})}))
                 (catch Exception e
                   {:status 500 :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:erro "Erro API externa" :detalhes (.getMessage e)})}))))
           ;; LISTAR ALIMENTOS REGISTRADOS
           (GET "/alimento" []
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string @alimentos)})

           ;; ATIVIDADE
           (POST "/atividade" req
             (let [desc (:atividade (-> req :body slurp (json/parse-string true)))
                   url (str "https://api.api-ninjas.com/v1/caloriesburned?activity="
                            (URLEncoder/encode desc "UTF-8"))]
               (try
                 (let [res (http/get url {:as :json
                                          :headers {"X-Api-Key" "RaqCO+Sd6+TUFytNoDUGRw==WcZX1CDynYAXO554"}})
                       item (first (:body res))
                       cal (:total_calories item)]
                   (if cal
                     (let [trans {:tipo "perda" :descricao desc :calorias cal :data (str (LocalDate/now))}]
                       (swap! atividades conj trans)
                       {:status 200 :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:mensagem "Atividade registrada." :transacao trans})})
                     {:status 404 :body (json/generate-string {:erro "Atividade não encontrada"})}))
                 (catch Exception e
                   {:status 500 :body (json/generate-string {:erro "Erro API externa"})}))))

           (GET "/atividade" []
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string @atividades)})

           (GET "/extrato" req
             (let [{:strs [inicio fim]} (:query-params req)
                   i (parse-data inicio)
                   f (parse-data fim)
                   transacoes (concat @alimentos @atividades)
                   extrato (filter #(dentro-do-periodo? (:data %) i f) transacoes)]
               {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:extrato extrato})}))

           (GET "/saldo" req
             (let [{:strs [data_inicio data_fim]} (:query-params req)
                   i (parse-data data_inicio)
                   f (parse-data data_fim)
                   trans (filter #(dentro-do-periodo? (:data %) i f)
                                 (concat @alimentos @atividades))
                   saldo (reduce (fn [acc t]
                                   (case (:tipo t)
                                     "ganho" (+ acc (:calorias t))
                                     "perda" (- acc (:calorias t))
                                     acc))
                                 0 trans)]
               {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:saldo saldo})}))

           (route/not-found
             {:status 404
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string {:erro "Rota inválida."})}))

(def app (wrap-defaults rotas api-defaults))

(defn -main []
  (println "Iniciando servidor em http://localhost:3000 ...")
  (run-jetty app {:port 3000 :join? false}))

;; FUNÇÃO DE TESTE COM POST DIRETO
(defn testar-post-atividade []
  (let [res (http/post "http://localhost:3000/atividade"
                       {:headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:atividade "corrida"})})]
    (println "Resposta do servidor:" (:status res))
    (println (slurp (:body res)))))
,