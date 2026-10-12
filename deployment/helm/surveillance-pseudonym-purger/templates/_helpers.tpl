{{/*
Expand the name of the chart.
*/}}
{{- define "surveillance-pseudonym-purger.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "surveillance-pseudonym-purger.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "surveillance-pseudonym-purger.fullversionname" -}}
{{- if .Values.istio.enable }}
{{- $name := include "surveillance-pseudonym-purger.fullname" . }}
{{- $version := regexReplaceAll "\\.+" .Chart.Version "-" }}
{{- printf "%s-%s" $name $version | trunc 63 }}
{{- else }}
{{- include "surveillance-pseudonym-purger.fullname" . }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "surveillance-pseudonym-purger.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "surveillance-pseudonym-purger.labels" -}}
helm.sh/chart: {{ include "surveillance-pseudonym-purger.chart" . }}
{{ include "surveillance-pseudonym-purger.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- if .Values.istio.enable }}
version: {{ .Chart.AppVersion | quote }}
{{- end }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.customLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "surveillance-pseudonym-purger.selectorLabels" -}}
{{- if .Values.istio.enable }}
app: {{ include "surveillance-pseudonym-purger.name" . }}
version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/name: {{ include "surveillance-pseudonym-purger.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Deployment labels
*/}}
{{- define "surveillance-pseudonym-purger.deploymentLabels" -}}
{{ if .Values.istio.enable -}}
istio-validate-jwt: "{{ .Values.istio.validateJwt | required ".Values.istio.validateJwt is required" }}"
{{- with .Values.deploymentLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "surveillance-pseudonym-purger.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "surveillance-pseudonym-purger.fullversionname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Environment Variables
*/}}
{{- define "surveillance-pseudonym-purger.env" -}}
{{- $envs := dict -}}
{{- if .Values.customEnvVars -}}
{{- range $key, $value := .Values.customEnvVars -}}
{{- if not (kindIs "invalid" $value) -}}
{{- $envs = set $envs $key $value }}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if .Values.debug.enable -}}
{{- $toolOptions := printf "%s %s" (get $envs "JAVA_TOOL_OPTIONS") .Values.debug.params | trim -}}
{{- $envs = set $envs "JAVA_TOOL_OPTIONS" $toolOptions -}}
{{- end -}}
{{- range $i, $key := keys $envs | sortAlpha -}}
{{- if $i }}
{{ end -}}
{{- $v := get $envs $key -}}
- name: {{ $key | quote }}
{{- if kindIs "string" $v }}
  value: {{ tpl $v $ | quote }}
{{- else }}
  value: {{ $v | quote }}
{{- end }}
{{- end -}}
{{- end -}}
