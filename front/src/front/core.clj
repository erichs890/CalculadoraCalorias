(ns front.core
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def base-url "http://localhost:3000")

(defn post-json [endpoint data]
  (let [response (http/post (str base-url endpoint)
                            {:body (json/generate-string data)
                             :headers {"Content-Type" "application/json"}
                             :as :json})]
    (:body response)))

(defn get-json [endpoint]
  (let [response (http/get (str base-url endpoint)
                           {:headers {"Accept" "application/json"}
                            :as :json})]
    (:body response)))

(defn cadastrar-dados-pessoais []
  (println "Informe sua altura (em metros): ")
  (flush)
  (let [altura (read-line)]
    (println "Informe seu peso (em kg): ")
    (flush)
    (let [peso (read-line)]
      (println "Informe sua idade: ")
      (flush)
      (let [idade (read-line)]
        (println "Informe seu sexo (M/F): ")
        (flush)
        (let [sexo (read-line)
              dados {:altura (Double/parseDouble altura)
                     :peso (Double/parseDouble peso)
                     :idade (Integer/parseInt idade)
                     :sexo (str/upper-case sexo)}
              res (post-json "/usuario" dados)]
          (println "Resposta do servidor:" res))))))

(defn consultar-dados-pessoais []
  (println "Consultando dados pessoais...")
  (let [res (get-json "/usuario")]
    (println "Dados do usuário:")
    (println res)))


(defn registrar-consumo-alimento []
  (println "Informe a descrição do alimento consumido:")
  (flush)
  (let [descricao (read-line)
        dados {:descricao descricao}
        res (post-json "/alimento" dados)]
    (println "Resposta do servidor:")
    (println res)))

(defn registrar-atividade-fisica []
  (println "Informe a atividade física realizada (ex: skiing):")
  (flush)
  (let [atividade (read-line)
        dados {:atividade atividade}
        res (post-json "/atividade" dados)]
    (println "Resposta do servidor:")
    (println res)))

(defn consultar-extrato []
  (println "Informe a data inicial (YYYY-MM-DD):")
  (flush)
  (let [data-inicial (read-line)]
    (println "Informe a data final (YYYY-MM-DD):")
    (flush)
    (let [data-final (read-line)
          endpoint (str "/extrato?inicio=" data-inicial "&fim=" data-final)
          res (get-json endpoint)]
      (println "Extrato de transações:")
      (println res))))

(defn consultar-saldo-calorias []
  (println "Informe a data inicial (YYYY-MM-DD):")
  (flush)
  (let [data-inicial (read-line)]
    (println "Informe a data final (YYYY-MM-DD):")
    (flush)
    (let [data-final (read-line)
          endpoint (str "/saldo?data_inicio=" data-inicial "&data_fim=" data-final)
          res (get-json endpoint)]
      (println "Saldo de calorias:")
      (println res))))


(defn menu []

  (println "\n=== Menu da Aplicação ===")
  (println "1. Consultar dados pessoais")
  (println "2. Registrar consumo de alimento")
  (println "3. Registrar atividade física")
  (println "4. Consultar extrato de transações")
  (println "5. Consultar saldo de calorias")
  (println "0. Sair")
  (print "Escolha uma opção: ")
  (flush)
  (read-line))

(defn -main []
  (let [choice (menu)]
    (case choice
      "1" (do
            (println "\n[1] Seus dados pessoais:")
            (consultar-dados-pessoais)
            (recur))

      "2" (do
            (println "\n[2] Registrar consumo de alimento")
            (registrar-consumo-alimento)
            (recur))
      "3" (do
            (println "\n[3] Registrar atividade física")
            (registrar-atividade-fisica)
            (recur))
      "4" (do
            (println "\n[4] Consultar extrato de transações")
            (consultar-extrato)
            (recur))
      "5" (do
            (println "\n[5] Consultar saldo de calorias")
            (consultar-saldo-calorias)
            (recur))
      "0" (println "Saindo... Obrigado!")
      (do
        (println "Opção inválida, tente novamente.")
        (recur)))))
(try
  (let [usuario (get-json "/usuario")]
    (when (or (nil? usuario) (empty? usuario))
      (cadastrar-dados-pessoais)))
  (catch Exception e
    (cadastrar-dados-pessoais)))


(-main)
