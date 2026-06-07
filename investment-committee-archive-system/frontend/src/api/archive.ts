import http from './http'

// ========== 通用类型 ==========
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

// ========== Project ==========
export interface Project {
  id?: number
  code: string
  name: string
  category?: string
  ownerId?: number
  amountWan?: number
  summary?: string
  status?: string
  scheduledMeetingAt?: string  // ISO date
  remark?: string
  createdAt?: string
  updatedAt?: string
  createdBy?: string
  updatedBy?: string
}

export const projectStatusOptions = [
  '草稿', '待审议', '审议中', '通过', '暂缓', '否决', '撤回',
]

export const projectCategoryOptions = ['股权类', '固收类', '混合类', '其他']

export function listProjects(params: { page?: number; size?: number; status?: string; keyword?: string }) {
  return http.get<PageResponse<Project>>('/projects', { params })
}

export function getProject(id: number) {
  return http.get<Project>(`/projects/${id}`)
}

export function createProject(data: Project) {
  return http.post<Project>('/projects', data)
}

export function updateProject(id: number, data: Project) {
  return http.put<Project>(`/projects/${id}`, data)
}

export function deleteProject(id: number) {
  return http.delete(`/projects/${id}`)
}

// ========== Proposal ==========
export interface Proposal {
  id?: number
  code: string
  title: string
  projectId: number
  type?: string
  summary?: string
  status?: string
  reviewedAt?: string
  decision?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export const proposalStatusOptions = [
  '草稿', '已提交', '审议中', '通过', '暂缓', '否决', '撤回',
]

export const proposalTypeOptions = ['主体', '担保', '联合', '调整', '终止', '其他']

export function listProposals(params: { page?: number; size?: number; projectId?: number; status?: string; keyword?: string }) {
  return http.get<PageResponse<Proposal>>('/proposals', { params })
}

export function getProposal(id: number) {
  return http.get<Proposal>(`/proposals/${id}`)
}

export function createProposal(data: Proposal) {
  return http.post<Proposal>('/proposals', data)
}

export function updateProposal(id: number, data: Proposal) {
  return http.put<Proposal>(`/proposals/${id}`, data)
}

export function deleteProposal(id: number) {
  return http.delete(`/proposals/${id}`)
}

// ========== Material ==========
export interface Material {
  id?: number
  proposalId: number
  title: string
  category?: string
  currentVersionId?: number
  status?: string
  description?: string
  tags?: string
  versionCount?: number
  createdAt?: string
  updatedAt?: string
}

export const materialStatusOptions = [
  '草稿', '评审中', '已通过', '已归档', '已作废',
]

export const materialCategoryOptions = [
  '尽调报告', '法律意见', '财务审计', '风险评估', '投委会决议', '其他',
]

export function listMaterials(params: { page?: number; size?: number; proposalId?: number; category?: string; status?: string; keyword?: string }) {
  return http.get<PageResponse<Material>>('/materials', { params })
}

export function getMaterial(id: number) {
  return http.get<Material>(`/materials/${id}`)
}

export function createMaterial(data: Material) {
  return http.post<Material>('/materials', data)
}

export function updateMaterial(id: number, data: Material) {
  return http.put<Material>(`/materials/${id}`, data)
}

export function deleteMaterial(id: number) {
  return http.delete(`/materials/${id}`)
}

// ========== MaterialVersion ==========
export interface MaterialVersion {
  id?: number
  materialId?: number
  versionNo?: number
  originalFilename?: string
  mimeType?: string
  fileSize?: number
  sha256?: string
  parseStatus?: string  // pending/running/success/failed
  parsedAt?: string
  parseError?: string
  uploadedBy?: string
  changeNote?: string
  createdAt?: string
}

export function listVersions(materialId: number) {
  return http.get<MaterialVersion[]>(`/materials/${materialId}/versions`)
}

export function uploadVersion(materialId: number, file: File, changeNote?: string) {
  const form = new FormData()
  form.append('file', file)
  if (changeNote) form.append('changeNote', changeNote)
  return http.post<MaterialVersion>(`/materials/${materialId}/versions`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function switchCurrentVersion(materialId: number, versionId: number) {
  return http.put(`/materials/${materialId}/versions/${versionId}/current`)
}

export function deleteVersion(materialId: number, versionId: number) {
  return http.delete(`/materials/${materialId}/versions/${versionId}`)
}

export function downloadVersionUrl(materialId: number, versionId: number) {
  return `/api/materials/${materialId}/versions/${versionId}/download`
}

export function reparseVersion(materialId: number, versionId: number) {
  return http.post(`/materials/${materialId}/versions/${versionId}/reparse`)
}

export interface Section {
  index: number
  title: string
  content: string
  length: number
  startOffset: number
  endOffset: number
}

export function listSections(materialId: number, versionId: number) {
  return http.get<Section[]>(`/materials/${materialId}/versions/${versionId}/sections`)
}
