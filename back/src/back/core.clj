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
(defonce extrato (atom []))
(defonce saldo (atom []))


;; FUNÇÕES AUXILIARES
(defn parse-data [s]
  (try (LocalDate/parse s) (catch Exception _ nil)))

(defn dentro-do-periodo? [data-str inicio fim]
  (let [data (parse-data data-str)]
    (and (not (.isBefore data inicio))
         (not (.isAfter data fim)))))

(defn extrair-numero [s]
  (try
    (Double/parseDouble (re-find #"\d+(?:\.\d+)?" s))
    (catch Exception _ 0.0)))


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

           (GET "/alimentos-opcoes" req
             (let [descricao (get-in req [:query-params "descricao"])
                   url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao="
                            (URLEncoder/encode descricao "UTF-8"))]
               (try
                 (let [res (http/get url {:as :json})]
                   {:status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/generate-string (:body res))})
                 (catch Exception e
                   {:status 500
                    :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:erro "Erro ao buscar opções de alimentos"
                                                 :detalhes (.getMessage e)})}))))

           ;; ALIMENTO
           (POST "/alimento" req
             (let [{:keys [descricao quantidade]} (-> req :body slurp (json/parse-string true))
                   quantidade (or (try (Double/parseDouble (str quantidade)) (catch Exception _ 1.0)) 1.0)
                   url (str "https://caloriasporalimentoapi.herokuapp.com/api/calorias/?descricao="
                            (URLEncoder/encode descricao "UTF-8"))]
               (try
                 (let [res (http/get url {:as :json})
                       item (first (:body res))
                       cal-str (:calorias item)
                       cal (extrair-numero cal-str)]
                   (if (pos? cal)
                     (let [cal-total (* cal quantidade)
                           trans {:tipo "ganho"
                                  :descricao descricao
                                  :quantidade quantidade
                                  :calorias cal-total
                                  :data (str (LocalDate/now))}]
                       (swap! alimentos conj trans)
                       (swap! extrato conj trans)
                       (swap! saldo conj trans)
                       {:status 200
                        :headers {"Content-Type" "application/json"}
                        :body (json/generate-string {:mensagem "Alimento registrado."
                                                     :transacao trans})})
                     {:status 404
                      :headers {"Content-Type" "application/json"}
                      :body (json/generate-string {:erro "Alimento não encontrado"})}))
                 (catch Exception e
                   {:status 500
                    :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:erro "Erro API externa"
                                                 :detalhes (.getMessage e)})})))) ;

           ;; LISTAR ALIMENTOS REGISTRADOS
           (GET "/alimento" []
             {:status 200
              :headers {"Content-Type" "application/json"}
              :body (json/generate-string @alimentos)})

           ;; ATIVIDADE
           (POST "/atividade" req
             (let [{:keys [atividade duracao]} (-> req :body slurp (json/parse-string true))
                   url (str "https://api.api-ninjas.com/v1/caloriesburned?activity="
                            (URLEncoder/encode atividade "UTF-8"))]
               (try
                 (let [res (http/get url {:as :json
                                          :headers {"X-Api-Key" "RaqCO+Sd6+TUFytNoDUGRw==WcZX1CDynYAXO554"}})
                       item (first (:body res))
                       cal-por-hora (:calories_per_hour item)]
                   (if cal-por-hora
                     (let [dur (or duracao 1) ; duração em minutos, padrão 1
                           cal-por-minuto (/ cal-por-hora 60.0)
                           total-cal (* cal-por-minuto dur)
                           trans {:tipo "perda"
                                  :descricao (str atividade " (" dur " min)")
                                  :calorias total-cal
                                  :data (str (LocalDate/now))}]
                       (swap! atividades conj trans)
                       (swap! extrato conj trans)
                       (swap! saldo conj trans)
                       {:status 200
                        :headers {"Content-Type" "application/json"}
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
                   f (parse-data fim)]
               (if (and i f)
                 (let [extrato (filter #(dentro-do-periodo? (:data %) i f) @extrato)]
                   {:status 200
                    :headers {"Content-Type" "application/json"}
                    :body (json/generate-string {:extrato extrato})})
                 {:status 400
                  :headers {"Content-Type" "application/json"}
                  :body (json/generate-string {:erro "Datas inválidas."})})))


           (GET "/saldo" [inicio fim]
             (let [data-inicio (parse-data inicio)
                   data-fim (parse-data fim)
                   todas-transacoes (concat @alimentos @atividades)
                   transacoes-no-periodo (filter (fn [{:keys [data]}]
                                                   (let [data-transacao (parse-data data)]
                                                     (and (not (.isBefore data-transacao data-inicio))
                                                          (not (.isAfter data-transacao data-fim)))))
                                                 todas-transacoes)
                   converter-calorias (fn [valor]
                                        (cond
                                          (string? valor) (Double/parseDouble (re-find #"\d+(?:\.\d+)?" valor))
                                          (number? valor) valor
                                          :else 0))
                   total-ganho (reduce + 0 (map #(converter-calorias (:calorias %))
                                                (filter #(= (:tipo %) "ganho") transacoes-no-periodo)))
                   total-perda (reduce + 0 (map #(converter-calorias (:calorias %))
                                                (filter #(= (:tipo %) "perda") transacoes-no-periodo)))
                   saldo-total (- total-ganho total-perda)]
               {:status 200
                :headers {"Content-Type" "application/json"}
                :body (json/generate-string {:ganho total-ganho
                                             :perda total-perda
                                             :saldo saldo-total})}))






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
