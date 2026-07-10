export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  readonly status: number
  readonly code: number

  constructor(
    message: string,
    status: number,
    code: number = status
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export const AUTH_TOKEN_KEY = 'flood-gis-auth-token'

const requestHeaders = (init?: RequestInit) => {
  const headers = new Headers(init?.headers)
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init?.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  headers.set('Accept', 'application/json')
  return headers
}

const parseResponse = async (response: Response): Promise<unknown> => {
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('application/json')) {
    if (!response.ok) throw new ApiError(`请求失败（HTTP ${response.status}）`, response.status)
    return response.text()
  }
  return response.json()
}

export const apiRequest = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetch(path, { ...init, headers: requestHeaders(init) })
  const payload = await parseResponse(response) as Partial<ApiResult<T>>
  if (!response.ok || (typeof payload.code === 'number' && payload.code >= 400)) {
    throw new ApiError(payload.message || `请求失败（HTTP ${response.status}）`, response.status, payload.code)
  }
  return payload.data as T
}

export const rawRequest = async <T>(path: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetch(path, { ...init, headers: requestHeaders(init) })
  const payload = await parseResponse(response) as T & { message?: string; error?: string }
  if (!response.ok) {
    throw new ApiError(payload?.message || payload?.error || `请求失败（HTTP ${response.status}）`, response.status)
  }
  return payload
}

export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : '请求失败，请稍后重试'
