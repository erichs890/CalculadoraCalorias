(ns back.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [cheshire.core :as json]
            [clj-http.client :as http])
  (:import (java.time LocalDate)
           (java.net URLEncoder)))

(defonce estado (atom {:usuario nil :transacoes []}))

(defn parse-data [s]
  (try (LocalDate/parse s) (catch Exception _ nil)))

(defn dentro-do-periodo? [data-str inicio fim]
  (let [data (parse-data data-str)]
    (and (not (.isBefore data inicio))
         (not (.isAfter data fim)))))

(defroutes rotas
           (GET "/" [] "Servidor de Calorias Ativo.")

           (POST "/usuario" req
             (let [body (-> req :body slurp (json/parse-string true))]
               (swap! estado assoc :usuario body)
               {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:mensagem "Usuário cadastrado com sucesso."})}))

           (GET "/usuario" []
             (let [u (:usuario @estado)]
               (if u
                 {:status 200 :headers {"Content-Type" "application/json"}
                  :body (json/generate-string u)}
                 {:status 200 :headers {"Content-Type" "application/json"}
                  :body (json/generate-string nil)})))

           (POST "/alimento" req
             (let [desc (:descricao (-> req :body slurp (json/parse-string true)))
                   url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao="
                            (URLEncoder/encode desc "UTF-8"))]
               (try
                 (let [res   (http/get url {:as :json})
                       itens (:body res) ;; <- já é um vetor
                       item  (first itens)
                       cal   (:calorias item)]
                   (if cal
                     (let [t {:tipo "ganho" :descricao desc :calorias cal :data (str (LocalDate/now))}]
                       (swap! estado update :transacoes conj t)
                       {:status 200 :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:mensagem "Alimento registrado." :transacao t})})
                     {:status 404 :headers {"Content-Type" "application/json"}
                      :body (json/generate-string {:erro "Alimento não encontrado"})}))
                 (catch Exception e
                   {:status 500 :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:erro "Erro API externa"
                                                 :detalhes (.getMessage e)})}))))


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
                     (let [t {:tipo "perda" :descricao desc :calorias cal :data (str (LocalDate/now))}]
                       (swap! estado update :transacoes conj t)
                       {:status 200 :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:mensagem "Atividade registrada." :transacao t})})
                     {:status 404 :body (json/generate-string {:erro "Atividade não encontrada"})}))
                 (catch Exception e
                   {:status 500 :body (json/generate-string {:erro "Erro API externa"})}))))

           (GET "/extrato" req
             (let [{:strs [inicio fim]} (:query-params req)
                   i (parse-data inicio)
                   f (parse-data fim)
                   extrato (filter #(dentro-do-periodo? (:data %) i f)
                                   (:transacoes @estado))]
               {:status 200 :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:extrato extrato})}))

           (GET "/saldo" req
             (let [{:strs [data_inicio data_fim]} (:query-params req)
                   i (parse-data data_inicio)
                   f (parse-data data_fim)
                   trans (filter #(dentro-do-periodo? (:data %) i f)
                                 (:transacoes @estado))
                   saldo (reduce (fn [acc t]
                                   (case (:tipo t)
                                     "ganho" (+ acc (:calorias t))
                                     "perda" (- acc (:calorias t))
                                     acc))
                                 0 trans)]
               {:status 200 :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:saldo saldo})}))

           (route/not-found "Rota inválida."))

(def app (wrap-defaults rotas api-defaults))

(defn -main [] (run-jetty app {:port 3000 :join? false}))
