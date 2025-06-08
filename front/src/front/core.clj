
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
    (if (seq res)
      (do
        (println "\n=== Dados Pessoais do Usuário ===")
        (println "Altura: " (:altura res) "m")
        (println "Peso:   " (:peso res) "kg")
        (println "Idade:  " (:idade res) "anos")
        (println "Sexo:   " (case (:sexo res)
                              "M" "Masculino"
                              "F" "Feminino"
                              (:sexo res))))
      (println "Nenhum dado pessoal encontrado."))))



(defn registrar-consumo-alimento []
  (println "Informe a descrição do alimento consumido:")
  (flush)
  (let [descricao (read-line)]
    (println "Informe a quantidade consumida (unidades):")
    (flush)
    (let [quantidade-str (read-line)
          quantidade (try
                       (Double/parseDouble quantidade-str)
                       (catch Exception _
                         (println "Quantidade inválida, usando 1 como padrão.")
                         1.0))
          dados {:descricao descricao
                 :quantidade quantidade}
          res (post-json "/alimento" dados)]
      (println "Resposta do servidor:")
      (println res))))


(defn registrar-atividade-fisica []
  (println "Informe a atividade física realizada (ex: skiing):")
  (flush)
  (let [atividade (read-line)]
    (println "Informe a duração (em minutos):")
    (flush)
    (let [duracao-str (read-line)
          duracao (try
                    (Double/parseDouble duracao-str)
                    (catch Exception _
                      (println "Duração inválida, usando 1 minuto como padrão.")
                      1.0))
          dados {:atividade atividade
                 :duracao duracao}
          res (post-json "/atividade" dados)]
      (println "Resposta do servidor:")
      (println res))))


(defn consultar-extrato []
  (println "Informe a data inicial (YYYY-MM-DD):")
  (flush)
  (let [data-inicial (read-line)]
    (println "Informe a data final (YYYY-MM-DD):")
    (flush)
    (let [data-final (read-line)
          endpoint (str "/extrato?inicio=" data-inicial "&fim=" data-final)
          res (get-json endpoint)
          extrato (:extrato res)]
      (println "\n=== Extrato de Transações ===")
      (if (seq extrato)
        (doseq [transacao extrato]
          (let [{:keys [tipo descricao quantidade calorias data]} transacao]
            (println "-----------------------------")
            (println "Tipo:      " (str/capitalize (name tipo)))
            (println "Descrição: " descricao)
            (when quantidade
              (println "Quantidade:" quantidade))
            (println "Calorias:  " calorias)
            (println "Data:      " data)))
        (println "Nenhuma transação encontrada no período informado.")))))

(defn consultar-saldo-calorias []
  (println "Informe a data inicial (YYYY-MM-DD):")
  (flush)
  (let [data-inicial (read-line)]
    (println "Informe a data final (YYYY-MM-DD):")
    (flush)
    (let [data-final (read-line)
          endpoint (str "/saldo?inicio=" data-inicial "&fim=" data-final)
          res (get-json endpoint)]
      (println "\n=== Saldo de Calorias ===")
      (println "Período: " data-inicial " até " data-final)
      (println "Total de calorias consumidas: " (:ganho res))
      (println "Total de calorias gastas:     " (:perda res))
      (println "Saldo final de calorias:      " (:saldo res)))))




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
  (let [usuario (get-json "/usuario")]
    (when (or (nil? usuario) (empty? usuario))
      (cadastrar-dados-pessoais)))
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