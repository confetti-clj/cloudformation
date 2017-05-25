(ns confetti.cloudformation
  (:refer-clojure :exclude [ref])
  (:require [confetti.policies :as pol]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [camel-snake-kebab.core :as case]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [amazonica.aws.cloudformation :as cformation]))

(defn validate-creds! [cred]
  (assert (and (string? (:access-key cred))
               (string? (:secret-key cred))) cred))

;; hardcoded value for Cloudfront according to
;; http://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/aws-properties-route53-aliastarget.html
(def CLOUDFRONT_HOSTED_ZONE "Z2FDTNDATAQYW2")

(defn pascal-case-kw [k]
  (if (keyword? k)
    (case/->PascalCaseString k)
    k))

(defn ref [resource]
  {:ref (pascal-case-kw resource)})

(defn attr [resource property]
  { "Fn::GetAtt" [(pascal-case-kw resource)
                  (pascal-case-kw property)]})

(defn join [& args]
  { "Fn::Join" [ "" args]})

(defn cf-get [map-name top-level second-level]
  { "Fn::FindInMap" [(pascal-case-kw map-name)
                     (pascal-case-kw top-level)
                     (pascal-case-kw second-level)]})

(defn website-endpoint [bucket-rid]
  (join (ref bucket-rid) (cf-get :website-endpoints (ref "AWS::Region") :suffix)))

;; rid = resource identifier
;; ie. the keys used in the resources map if the cloudfront template

(defn hosted-zone [domain-rid]
  {:type "AWS::Route53::HostedZone"
   :properties {:hosted-zone-tags [{:key "confetti"
                                    :value "site-xyz"}]
                :name (ref domain-rid)}})

(defn cloudfront-record-set [cloudfront-dist-rid hosted-zone-rid domain-rid]
  {:type "AWS::Route53::RecordSet"
   :properties {:alias-target {:d-n-s-name (attr cloudfront-dist-rid :domain-name)
                               :hosted-zone-id CLOUDFRONT_HOSTED_ZONE}
                ;; This is a bit dirty probably should have different args for this case
                :hosted-zone-id (if (string? hosted-zone-rid) hosted-zone-rid (ref hosted-zone-rid))
                :name (ref domain-rid)
                :type "A"}})

(defn bucket []
  {:type "AWS::S3::Bucket"
   :properties {:access-control "PublicRead"
                :website-configuration {:error-document "error.html"
                                        :index-document "index.html"}}})

(defn bucket-policy [bucket-rid]
  {:type "AWS::S3::BucketPolicy"
   :properties {:bucket (ref bucket-rid)
                :policy-document (pol/bucket-policy (ref bucket-rid))}})

(defn user-policy [bucket-rid]
  {:policy-name (join (ref bucket-rid) "-S3-BucketFullAccess")
   :policy-document (pol/user-policy (ref bucket-rid))})

(defn user [& policies]
  {:type "AWS::IAM::User"
   :properties {:policies policies}})

(defn access-key [user-rid]
  {:type "AWS::IAM::AccessKey"
   :properties {:status "Active"
                :user-name (ref user-rid)}})

(def mappings
  ;; http://docs.aws.amazon.com/AmazonS3/latest/dev/WebsiteEndpoints.html
  {:website-endpoints
   {"us-east-1"      {:suffix ".s3-website-us-east-1.amazonaws.com"}
    "us-west-1"      {:suffix ".s3-website-us-west-1.amazonaws.com"}
    "us-west-2"      {:suffix ".s3-website-us-west-2.amazonaws.com"}
    "eu-west-1"      {:suffix ".s3-website-eu-west-1.amazonaws.com"}
    "eu-central-1"   {:suffix ".s3-website-eu-central-1.amazonaws.com"}
    "ap-northeast-1" {:suffix ".s3-website-ap-northeast-1.amazonaws.com"}
    "ap-northeast-2" {:suffix ".s3.ap-northeast-2.amazonaws.com"}
    "ap-southeast-1" {:suffix ".s3-website-ap-southeast-1.amazonaws.com"}
    "ap-southeast-2" {:suffix ".s3-website-ap-southeast-2.amazonaws.com"}
    "sa-east-1"      {:suffix ".s3-website-sa-east-1.amazonaws.com"}
    "cn-north-1"     {:suffix ".s3-website.cn-north-1.amazonaws.com.cn"}}
   :hosted-zones
   {"us-east-1"      {:zone "Z3AQBSTGFYJSTF"}
    "us-west-1"      {:zone "Z2F56UZL2M1ACD"}
    "us-west-2"      {:zone "Z3BJ6K6RIION7M"}
    "eu-west-1"      {:zone "Z1BKCTXD74EZPE"}
    "eu-central-1"   {:zone "Z21DNDUVLTQW6Q"}
    "ap-northeast-1" {:zone "Z2M4EHUR26P7ZW"}
    "ap-northeast-2" {:zone "Z3W03O7B5YMIYP"}
    "ap-southeast-1" {:zone "Z3O0J2DXBE1FTB"}
    "ap-southeast-2" {:zone "Z1WCIGYICN2BYD"}
    "sa-east-1"      {:zone "Z7KQH4QJS55SO"}}})

(defn cloudfront-dist [domain-rid bucket-rid]
  {:type "AWS::CloudFront::Distribution"
   :properties {:distribution-config
                {:comment "CDN for S3 backed website"
                 :origins [{:domain-name (website-endpoint bucket-rid)
                            :id          (website-endpoint bucket-rid)
                            :custom-origin-config {:origin-protocol-policy "http-only"}}]
                 :default-cache-behavior {:target-origin-id (website-endpoint bucket-rid)
                                          :forwarded-values {:query-string "false"}
                                          :viewer-protocol-policy "allow-all"
                                          :compress true}
                 :enabled "true"
                 :default-root-object "index.html"
                 :aliases [(ref domain-rid)]}}})

;; Probably all the cond-> trickery in the `template` function could be replaced
;; by cleverly using Cloudformation conditions and whatnot but I'm just too lazy
;; to really understand all of Cloudformation's features

(defn template
  "Generate a Cloudformation template for a static site using S3, Cloudfront and optionally Route53.

   If the `dns?` option is specified a HostedZone and RecordSet will be created.
   If the `dns?` option is specified and also a `hosted-zone-id` is provided the HostedZone
   identified by this ID will be used and no new HostedZone will be created."
  [{:keys [dns? hosted-zone-id] :as opts}]
  {:description "Created by github.com/confetti-clj"
   :parameters {:user-domain {:type "String"}}
   :mappings    mappings
   :resources (cond-> {:site-bucket            (bucket)
                       :bucket-policy          (bucket-policy :site-bucket)
                       :bucket-user            (user (user-policy :site-bucket))
                       :bucket-user-access-key (access-key :bucket-user)
                       :site-cdn               (cloudfront-dist :user-domain :site-bucket)}

                (and dns? (not hosted-zone-id))
                (assoc :hosted-zone     (hosted-zone :user-domain))

                (and dns? (not hosted-zone-id))
                (assoc :zone-record-set (cloudfront-record-set :site-cdn :hosted-zone :user-domain))

                (and dns? hosted-zone-id)
                (assoc :zone-record-set (cloudfront-record-set :site-cdn hosted-zone-id :user-domain)))

   :outputs (cond-> {:bucket-name {:value (ref :site-bucket)
                                   :description "Name of the S3 bucket"}
                     :cloudfront-id {:value (ref :site-cdn)
                                     :description "ID of CloudFront distribution"}
                     :cloudfront-url {:value (attr :site-cdn :domain-name)
                                      :description "URL to access CloudFront distribution"}
                     :access-key {:value (ref :bucket-user-access-key)
                                  :description "AccessKey that can only access bucket"}
                     :secret-key {:value (attr :bucket-user-access-key :secret-access-key)
                                  :description "Secret for AccessKey that can only access bucket"}
                     :website-url {:value (join "http://" (if dns? (ref :zone-record-set) (ref :user-domain)))
                                   :description "URL of your site"}}
              (and dns? (not hosted-zone-id))
              (assoc :hosted-zone-id {:value (ref :hosted-zone)
                                      :description "ID of HostedZone"})

              (and dns? hosted-zone-id)
              (assoc :hosted-zone-id {:value hosted-zone-id
                                      :description "ID of HostedZone"}))})

(defn map->cf-params [m]
  (-> (fn [p [k v]]
        (conj p {:parameter-key (pascal-case-kw k)
                 :parameter-value v}))
      (reduce [] m)))

(defn run-template [cred stack-name template params]
  (validate-creds! cred)
  (let [tplate (json/write-str (transform-keys pascal-case-kw template))]
    (cformation/create-stack
     cred
     :stack-name stack-name
     :template-body tplate
     :capabilities ["CAPABILITY_IAM"]
     :parameters (map->cf-params params))))

(defn get-events [cred stack-id]
  (validate-creds! cred)
  (:stack-events (cformation/describe-stack-events cred {:stack-name stack-id})))

(defn get-outputs [cred stack-id]
  (validate-creds! cred)
  (let [sanitize #(for [o %]
                   [(case/->kebab-case-keyword (:output-key o))
                    (dissoc o :output-key)])
        [stack-info] (:stacks (cformation/describe-stacks cred {:stack-name stack-id}))]
    (when-not (= "CREATE_COMPLETE" (:stack-status stack-info))
      (throw (ex-info "Stack status is other than CREATE_COMPLETE" {:stack-info stack-info})))
    (->> stack-info :outputs sanitize (into {}))))

(defn succeeded? [events]
  (->> events
       (filter #(= (:resource-status %) "CREATE_COMPLETE"))
       (filter #(= (:resource-type %) "AWS::CloudFormation::Stack"))
       seq
       boolean))

(defn failed? [events]
  (->> events
       (filter #(= (:resource-status %) "ROLLBACK_COMPLETE"))
       (filter #(= (:resource-type %) "AWS::CloudFormation::Stack"))
       seq
       boolean))

(comment
  (require '[amazonica.aws.s3 :as s3]
           '[amazonica.aws.route53 :as r53])

  (get-outputs "arn:aws:cloudformation:us-east-1:297681564547:stack/subsdufysb-martinklepsch-org-confetti-static-site/62436cc0-9290-11e5-9caa-5001b4b81a9a")

  (succeeded? (get-events "arn:aws:cloudformation:us-east-1:297681564547:stack/static-site-xyz/5aa62560-8f03-11e5-a56f-50e24162947c"))

  (run-template "static-site-xyz"
                (template {:dns? false})
                {:user-domain "tenzing.martinklepsch.org"})

  (s3/put-object :bucket-name "static-site-cljs-io-sitebucket-1969npf1zvwoh"
                 :key "index.html"
                 :file (io/file "index.html"))

  (cformation/delete-stack :stack-name "static-site-cljs-io")

  (r53/list-hosted-zones-by-name)
  ;; Get Nameservers
  (->> (r53/list-resource-record-sets :hosted-zone-id "Z19P2YAHTI3R3Z")
       :resource-record-sets
       (filter (fn [r] (= "NS" (:type r))))
       (mapcat :resource-records)
       (map :value))

  ;; (cformation/describe-stack-events :stack-name "static-site-456")

  (slurp (io/file "cf-template.json"))
  (= (old-template) (template)))
