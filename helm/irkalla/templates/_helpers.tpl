{{/* Generate basic labels */}}
{{- define "irkalla.labels" }}
app: "irkalla"
shortname: "irkalla"
team: ror
app.kubernetes.io/managed-by: Helm
{{- end }}

{{/* Generate common Helm ownership annotations */}}
{{- define "irkalla.annotations" }}
meta.helm.sh/release-name: {{ .Release.Name }}
meta.helm.sh/release-namespace: {{ .Release.Namespace }}
{{- end }}