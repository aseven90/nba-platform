{{- define "nba-common.all" -}}
{{ include "nba-common.serviceaccount" . }}
---
{{ include "nba-common.service" . }}
---
{{ include "nba-common.deployment" . }}
{{- if .Values.autoscaling.enabled }}
---
{{ include "nba-common.hpa" . }}
{{- end }}
{{- if .Values.pdb.enabled }}
---
{{ include "nba-common.pdb" . }}
{{- end }}
{{- if .Values.ingress.enabled }}
---
{{ include "nba-common.ingress" . }}
{{- end }}
{{- if and .Values.openshift.enabled .Values.openshift.route.enabled }}
---
{{ include "nba-common.route" . }}
{{- end }}
{{- if .Values.serviceMonitor.enabled }}
---
{{ include "nba-common.servicemonitor" . }}
{{- end }}
{{- end -}}

{{- define "nba-common.serviceaccount" -}}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
automountServiceAccountToken: false
{{- end -}}

{{- define "nba-common.service" -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  type: ClusterIP
  selector:
    {{- include "nba-common.selectorLabels" . | nindent 4 }}
  ports:
    - name: http
      port: 80
      targetPort: http
      appProtocol: http
{{- end -}}

{{- define "nba-common.deployment" -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0
  selector:
    matchLabels:
      {{- include "nba-common.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "nba-common.labels" . | nindent 8 }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: /actuator/prometheus
        prometheus.io/port: {{ .Values.containerPort | quote }}
        {{- with .Values.podAnnotations }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    spec:
      serviceAccountName: {{ include "nba-common.fullname" . }}
      automountServiceAccountToken: false
      {{- with .Values.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      securityContext:
        {{- include "nba-common.podSecurityContext" . | nindent 8 }}
      terminationGracePeriodSeconds: {{ .Values.terminationGracePeriodSeconds }}
      {{- if .Values.topologySpread.enabled }}
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              {{- include "nba-common.selectorLabels" . | nindent 14 }}
        - maxSkew: 1
          topologyKey: kubernetes.io/hostname
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              {{- include "nba-common.selectorLabels" . | nindent 14 }}
      {{- end }}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: app
          image: "{{ .Values.image.repository }}:{{ .Values.image.tag | default .Chart.AppVersion }}"
          imagePullPolicy: {{ .Values.image.pullPolicy }}
          ports:
            - name: http
              containerPort: {{ .Values.containerPort }}
          env:
            - name: PORT
              value: {{ .Values.containerPort | quote }}
            {{- range $name, $value := .Values.env }}
            - name: {{ $name }}
              value: {{ $value | quote }}
            {{- end }}
          {{- if .Values.envFromSecret }}
          envFrom:
            - secretRef:
                name: {{ .Values.envFromSecret }}
          {{- end }}
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 5
            failureThreshold: 36
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
            failureThreshold: 3
          lifecycle:
            preStop:
              # Let endpoints and mesh proxies stop routing before shutdown starts.
              exec: { command: ["sleep", "5"] }
          resources:
            {{- toYaml .Values.resources | nindent 12 }}
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities:
              drop: ["ALL"]
          volumeMounts:
            - name: tmp
              mountPath: /tmp
      volumes:
        - name: tmp
          emptyDir:
            sizeLimit: 256Mi
{{- end -}}

{{- define "nba-common.hpa" -}}
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "nba-common.fullname" . }}
  minReplicas: {{ .Values.autoscaling.minReplicas }}
  maxReplicas: {{ .Values.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ .Values.autoscaling.targetCPUUtilizationPercentage }}
    {{- with .Values.autoscaling.extraMetrics }}
    {{- toYaml . | nindent 4 }}
    {{- end }}
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 0
      policies:
        - type: Percent
          value: 100
          periodSeconds: 30
    scaleDown:
      stabilizationWindowSeconds: 300
{{- end -}}

{{- define "nba-common.pdb" -}}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  minAvailable: {{ .Values.pdb.minAvailable }}
  selector:
    matchLabels:
      {{- include "nba-common.selectorLabels" . | nindent 6 }}
{{- end -}}

{{- define "nba-common.ingress" -}}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
  {{- with .Values.ingress.annotations }}
  annotations:
    {{- toYaml . | nindent 4 }}
  {{- end }}
spec:
  {{- with .Values.ingress.className }}
  ingressClassName: {{ . }}
  {{- end }}
  {{- with .Values.ingress.tls }}
  tls:
    {{- toYaml . | nindent 4 }}
  {{- end }}
  rules:
    - host: {{ .Values.ingress.host | quote }}
      http:
        paths:
          - path: /tmf-api
            pathType: Prefix
            backend:
              service:
                name: {{ include "nba-common.fullname" . }}
                port:
                  name: http
{{- end -}}

{{- define "nba-common.route" -}}
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  {{- with .Values.openshift.route.host }}
  host: {{ . }}
  {{- end }}
  path: /tmf-api
  to:
    kind: Service
    name: {{ include "nba-common.fullname" . }}
  port:
    targetPort: http
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
{{- end -}}

{{- define "nba-common.servicemonitor" -}}
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: {{ include "nba-common.fullname" . }}
  labels:
    {{- include "nba-common.labels" . | nindent 4 }}
spec:
  selector:
    matchLabels:
      {{- include "nba-common.selectorLabels" . | nindent 6 }}
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 30s
{{- end -}}
