import http, { getData } from './http'

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

export async function listProjects(params: { page?: number; size?: number; status?: string; keyword?: string }): Promise<PageResponse<Project>> {
  return getData<PageResponse<Project>>(await http.get<any>('/projects', { params }))
}

export async function getProject(id: number): Promise<Project> {
  return getData<Project>(await http.get<any>(`/projects/${id}`))
}

export async function createProject(data: Project): Promise<Project> {
  return getData<Project>(await http.post<any>('/projects', data))
}

export async function updateProject(id: number, data: Project): Promise<Project> {
  return getData<Project>(await http.put<any>(`/projects/${id}`, data))
}

export async function deleteProject(id: number): Promise<void> {
  await http.delete(`/projects/${id}`)
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

export async function listProposals(params: { page?: number; size?: number; projectId?: number; status?: string; keyword?: string }): Promise<PageResponse<Proposal>> {
  return getData<PageResponse<Proposal>>(await http.get<any>('/proposals', { params }))
}

export async function getProposal(id: number): Promise<Proposal> {
  return getData<Proposal>(await http.get<any>(`/proposals/${id}`))
}

export async function createProposal(data: Proposal): Promise<Proposal> {
  return getData<Proposal>(await http.post<any>('/proposals', data))
}

export async function updateProposal(id: number, data: Proposal): Promise<Proposal> {
  return getData<Proposal>(await http.put<any>(`/proposals/${id}`, data))
}

export async function deleteProposal(id: number): Promise<void> {
  await http.delete(`/proposals/${id}`)
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

export async function listMaterials(params: { page?: number; size?: number; proposalId?: number; category?: string; status?: string; keyword?: string }): Promise<PageResponse<Material>> {
  return getData<PageResponse<Material>>(await http.get<any>('/materials', { params }))
}

export async function getMaterial(id: number): Promise<Material> {
  return getData<Material>(await http.get<any>(`/materials/${id}`))
}

export async function createMaterial(data: Material): Promise<Material> {
  return getData<Material>(await http.post<any>('/materials', data))
}

export async function updateMaterial(id: number, data: Material): Promise<Material> {
  return getData<Material>(await http.put<any>(`/materials/${id}`, data))
}

export async function deleteMaterial(id: number): Promise<void> {
  await http.delete(`/materials/${id}`)
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

export async function listVersions(materialId: number): Promise<MaterialVersion[]> {
  return getData<MaterialVersion[]>(await http.get<any>(`/materials/${materialId}/versions`))
}

export async function uploadVersion(materialId: number, file: File, changeNote?: string): Promise<MaterialVersion> {
  const form = new FormData()
  form.append('file', file)
  if (changeNote) form.append('changeNote', changeNote)
  return getData<MaterialVersion>(await http.post<any>(`/materials/${materialId}/versions`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }))
}

export async function switchCurrentVersion(materialId: number, versionId: number): Promise<void> {
  await http.put(`/materials/${materialId}/versions/${versionId}/current`)
}

export async function deleteVersion(materialId: number, versionId: number): Promise<void> {
  await http.delete(`/materials/${materialId}/versions/${versionId}`)
}

export function downloadVersionUrl(materialId: number, versionId: number) {
  return `/api/materials/${materialId}/versions/${versionId}/download`
}

export async function reparseVersion(materialId: number, versionId: number): Promise<void> {
  await http.post(`/materials/${materialId}/versions/${versionId}/reparse`)
}

export interface Section {
  index: number
  title: string
  content: string
  length: number
  startOffset: number
  endOffset: number
}

export async function listSections(materialId: number, versionId: number): Promise<Section[]> {
  return getData<Section[]>(await http.get<any>(`/materials/${materialId}/versions/${versionId}/sections`))
}

// ========== Batch Upload ==========
export async function batchUploadMaterials(
  proposalId: number,
  files: File[],
  defaultCategory?: string,
  defaultTags?: string,
  onProgress?: (percent: number) => void
): Promise<Material[]> {
  const form = new FormData()
  form.append('proposalId', String(proposalId))
  files.forEach(f => form.append('files', f))
  if (defaultCategory) form.append('defaultCategory', defaultCategory)
  if (defaultTags) form.append('defaultTags', defaultTags)
  return getData<Material[]>(await http.post<any>('/materials/batch', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress ? (e: any) => {
      if (e.total) onProgress(Math.round((e.loaded / e.total) * 100))
    } : undefined,
  }))
}

// ========== Regenerate Summary ==========
export async function regenerateSummary(proposalId: number): Promise<Proposal> {
  return getData<Proposal>(await http.post<any>(`/proposals/${proposalId}/regenerate-summary`))
}
