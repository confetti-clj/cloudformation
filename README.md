<img src="https://cloud.githubusercontent.com/assets/97496/11431670/0ef1bb58-949d-11e5-83f7-d07cf1dd89c7.png" alt="confetti logo" align="right" />

# confetti/cloudformation

Generate CloudFormations templates suitable for static site and single page app deployments and run them.

[](dependency)
```clojure
[confetti/cloudformation "0.1.6"] ;; latest release
```
[](/dependency)

## Changes

#### 0.1.6

- Enable compression by default. Previously not possible via CloudFormation. [#5](https://github.com/confetti-clj/cloudformation/issues/5)

#### 0.1.5

- Address a misconfiguration of the S3 website endpoint origin where ViewerProtocolPolicy was set to Match-Viewer but should have been HTTP Only since S3 website endpoints generally don't support HTTPS (yet).

#### 0.1.4

- add a `:hosted-zone-id` option to `confetti.cloudformation/template` that can be used to specify an existing HostedZone to which the new RecordSet should be added

#### 0.1.3

- register website-url stack output no matter if Route53 is used or not

#### 0.1.2

- better error reporting when outputs are fetched for a deleted or otherwise unavailable stack


