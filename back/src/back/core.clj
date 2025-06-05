(ns back.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clj-http.client :as http]
            [cheshire.core :as json])
  (:import (java.time LocalDate)
           (java.net URLEncoder)))

;; Átomo para guardar o estado da aplicação
(def app-state (atom {:usuario nil
                      :transacoes []}))

;; Funções auxiliares para datas
(defn parse-data [data-str]
  (try
    (java.time.LocalDate/parse data-str)
    (catch Exception _ nil)))

(defn transacao-entre? [transacao data-inicio data-fim]
  (let [data (parse-data (:data transacao))]
    (and (not (.isBefore data data-inicio))
         (not (.isAfter data data-fim)))))

;; Rotas da API
(defroutes app-routes
           (GET "/" [] "KKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK")

           ;; Usuário - cadastrar e consultar
           (POST "/usuario" req
                 (let [dados (json/parse-string (slurp (:body req)) true)]
                   (swap! app-state assoc :usuario dados)
                   {:status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:mensagem "Usuário registrado com sucesso."})}))

           (GET "/usuario" []
                (let [usuario (:usuario @app-state)]
                  (if usuario
                    {:status 200
                     :headers {"Content-Type" "application/json"}
                     :body (json/generate-string usuario)}
                    {:status 404
                     :headers {"Content-Type" "application/json"}
                     :body (json/generate-string {:erro "Usuário não encontrado."})})))

           ;; Registrar consumo de alimento
           (POST "/alimento" req
                 (let [body-data   (json/parse-string (slurp (:body req)) true)
                       descricao   (:descricao body-data)
                       api-url     (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/"
                                        "?descricao=" (URLEncoder/encode descricao "UTF-8"))]
                   (try
                     (let [resp       (http/get api-url {:as :json})
                           resultados (get-in resp [:body])
                           primeira   (first resultados)]
                       (if (and primeira (contains? primeira :calorias))
                         (let [calorias       (:calorias primeira)
                               transacao      {:tipo      "ganho"
                                               :descricao descricao
                                               :calorias  calorias
                                               :data      (str (LocalDate/now))}
                               _              (swap! app-state update :transacoes conj transacao)]
                           {:status  200
                            :headers {"Content-Type" "application/json"}
                            :body    (json/generate-string
                                       {:mensagem  "Consumo registrado com sucesso."
                                        :transacao transacao})})
                         {:status  404
                          :headers {"Content-Type" "application/json"}
                          :body    (json/generate-string
                                     {:erro (str "Alimento não encontrado para '" descricao "'.")})}))
                     (catch Exception e
                       {:status  500
                        :headers {"Content-Type" "application/json"}
                        :body    (json/generate-string
                                   {:erro     "Falha ao consultar API de calorias."
                                    :detalhes (.getMessage e)})}))))

           ;; Registrar realização de atividade física
           (POST "/atividade" req
                 (let [body-data (json/parse-string (slurp (:body req)) true)
                       atividade (:atividade body-data)
                       url (str "https://api.api-ninjas.com/v1/caloriesburned?activity="
                                (URLEncoder/encode atividade "UTF-8"))]
                   (try
                     (let [resp       (http/get url {:headers {"X-Api-Key" "RaqCO+Sd6+TUFytNoDUGRw==WcZX1CDynYAXO554"}
                                                     :as :json})
                           resultados (get-in resp [:body])
                           primeira   (first resultados)]
                       (if (and primeira (contains? primeira :total_calories))
                         (let [calorias   (:total_calories primeira)
                               transacao  {:tipo      "perda"
                                           :descricao atividade
                                           :calorias  calorias
                                           :data      (str (LocalDate/now))}
                               _          (swap! app-state update :transacoes conj transacao)]
                           {:status 200
                            :headers {"Content-Type" "application/json"}
                            :body (json/generate-string
                                    {:mensagem "Atividade registrada com sucesso."
                                     :transacao transacao})})
                         {:status 404
                          :headers {"Content-Type" "application/json"}
                          :body (json/generate-string
                                  {:erro (str "Atividade não encontrada: " atividade)})}))
                     (catch Exception e
                       {:status 500
                        :headers {"Content-Type" "application/json"}
                        :body (json/generate-string
                                {:erro "Erro ao consultar API de atividade física."
                                 :detalhes (.getMessage e)})}))))

           ;; Consultar extrato de transações por período
           (GET "/extrato" req
                (let [params (:query-params req)
                      data-inicio (some-> (get params "data_inicio") parse-data)
                      data-fim    (some-> (get params "data_fim") parse-data)
                      transacoes  (:transacoes @app-state)
                      filtrar (fn [t]
                                (if (and data-inicio data-fim)
                                  (transacao-entre? t data-inicio data-fim)
                                  true))
                      extrato (filter filtrar transacoes)]
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body (json/generate-string {:extrato (vec extrato)})}))

           (GET "/saldo" req
                (let [params (:query-params req)
                      _ (println "Params:" params)
                      data-inicio (some-> (get params "data_inicio") parse-data)
                      data-fim    (some-> (get params "data_fim") parse-data)
                      _ (println "Data início:" data-inicio "Data fim:" data-fim)
                      transacoes  (:transacoes @app-state)
                      filtrar (fn [t]
                                (if (and data-inicio data-fim)
                                  (transacao-entre? t data-inicio data-fim)
                                  true))
                      transacoes-filtradas (filter filtrar transacoes)
                      _ (println "Transações filtradas:" (count transacoes-filtradas))
                      saldo (reduce (fn [acc t]
                                      (case (:tipo t)
                                        "ganho" (+ acc (:calorias t))
                                        "perda" (- acc (:calorias t))
                                        (do (println "Tipo desconhecido:" (:tipo t)) acc)))
                                    0
                                    transacoes-filtradas)]
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body (json/generate-string {:saldo saldo})}))



           ;; Fallback para rotas não encontradas
           (route/not-found "Recurso não encontrado"))

;; Middleware para API padrão
(def app
  (wrap-defaults app-routes api-defaults))

;; Inicializador do servidor
(defn -main []
  (run-jetty app {:port 3000 :join? false}))

