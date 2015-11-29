(ns confetti.policies)

(defn join [& args]
  { "Fn::Join" [ "" args]})

(defn allow-statement [actions resources]
  {:effect   "Allow"
   :action   actions
   :resource resources})

;; Actions on CloudFront distributions can't be limited to specific resoures unfortunately
;; http://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/UsingWithIAM.html#CloudFront_ARN_Format
(defn user-policy [bucket-ref]
  {:version "2012-10-17"
   :statement [(allow-statement ["cloudfront:CreateInvalidation"] ["*"])
               (allow-statement ["s3:ListBucket"] [(join "arn:aws:s3:::" bucket-ref)])
               (allow-statement ["*"] [(join "arn:aws:s3:::" bucket-ref "/*")])]})

(defn bucket-policy [bucket-ref]
  {:version "2012-10-17"
   :statement [{:sid "PublicReadGetObject"
                :effect "Allow"
                :principal "*"
                :action ["s3:GetObject"]
                :resource (join "arn:aws:s3:::" bucket-ref "/*")}]})
