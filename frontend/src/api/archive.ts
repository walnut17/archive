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
  customerName?: string
  masked?: boolean
  displayName?: string
  displayAmount?: string
  unmaskRequestUrl?: string
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

export async function exportProject(id: number, format: 'pdf' | 'xlsx' = 'pdf'): Promise<Blob> {
  const res = await http.get(`/projects/${id}/export`, { params: { format }, responseType: 'blob' })
  return res.data
}

export async function exportProjectsList(type: string): Promise<Blob> {
  const res = await http.get('/projects/export', { params: { format: 'xlsx', type }, responseType: 'blob' })
  return res.data
}

export async function requestUnmask(id: number): Promise<{ unmaskRequestUrl: string }> {
  return getData(await http.post<any>(`/projects/${id}/unmask-request`))
}

export async function rollbackProject(id: number, targetVersion: number): Promise<Project> {
  return getData<Project>(await http.post<any>(`/projects/${id}/rollback`, { targetVersion }))
}

// ========== Project Board ==========
export interface ProjectBoardItem {
  id: number
  code: string
  name: string
  region?: string
  stage?: string
  amount?: number
  proposalCount?: number
  todoCount?: number
  lastUpdated?: string
  masked?: boolean
}

export interface BoardResponse {
  view: string
  items?: ProjectBoardItem[]
  kanban?: Record<string, ProjectBoardItem[]>
  total: number
}

export async function listProjectBoard(params: {
  view?: string
  region?: string
  stage?: string
  sort?: string
  order?: string
  page?: number
  size?: number
}): Promise<BoardResponse> {
  const res = await http.get<any>('/projects/board', { params })
  return res.data?.data ?? res.data
}

// ========== Fact Events ==========
export interface FactEventDiff {
  before?: string
  after?: string
  evidenceSnippet?: string
}

export interface ProjectFactEvent {
  id: number
  projectId: number
  factType: string
  eventType: string
  factValue?: string
  evidence?: string
  createdAt?: string
}

export async function getFactEventDiff(projectId: number, eventId: number): Promise<FactEventDiff> {
  return getData<FactEventDiff>(await http.get<any>(`/projects/${projectId}/fact-events/${eventId}/diff`))
}

// ========== Material Preview ==========
export function getMaterialPreviewUrl(materialId: number, version?: number): string {
  const base = `/api/materials/${materialId}/preview`
  return version != null ? `${base}?version=${version}` : base
}

// ========== Import ==========
export interface ImportBatch {
  id: number
  type: string
  total: number
  success: number
  failed: number
  status?: string
  createdAt?: string
}

export interface ImportError {
  id?: number
  batchId?: number
  row: number
  column?: number
  errorMsg?: string
}

export async function importExcel(type: string, file: File): Promise<ImportBatch> {
  const form = new FormData()
  form.append('file', file)
  const res = await http.post<any>(`/admin/import/${type}`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data?.data ?? res.data
}

export async function getImportErrors(batchId: number): Promise<ImportError[]> {
  const res = await http.get<any>(`/admin/import/${batchId}/errors`)
  return res.data?.data ?? res.data ?? []
}

// ========== Recycle Bin ==========
export async function listRecycleBin(entityType: string, limit = 50): Promise<Record<string, unknown>[]> {
  return getData<Record<string, unknown>[]>(await http.get<any>(`/recycle-bin/${entityType}`, { params: { limit } }))
}

export async function restoreRecycleBin(entityType: string, id: number): Promise<void> {
  await http.post(`/recycle-bin/${entityType}/${id}/restore`)
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

// ========== Dict Admin ==========
export interface DictType {
  id?: number
  typeCode: string
  typeName: string
  description?: string
  sortOrder?: number
  isSystem?: boolean
}

export interface DictItem {
  id?: number
  typeCode: string
  itemKey: string
  itemValue: string
  sortOrder?: number
  isDefault?: boolean
  enabled?: boolean
  isSystem?: boolean
  remark?: string
}

export async function listDictTypes(): Promise<DictType[]> {
  return getData<DictType[]>(await http.get<any>('/dict/types'))
}

export async function createDictType(data: Partial<DictType>): Promise<DictType> {
  return getData<DictType>(await http.post<any>('/dict/types', data))
}

export async function listDictItems(typeCode: string): Promise<DictItem[]> {
  return getData<DictItem[]>(await http.get<any>(`/dict/items?typeCode=${typeCode}`))
}

export async function createDictItem(data: Partial<DictItem>): Promise<DictItem> {
  return getData<DictItem>(await http.post<any>('/dict/items', data))
}

export async function updateDictItem(id: number, data: Partial<DictItem>): Promise<DictItem> {
  return getData<DictItem>(await http.put<any>(`/dict/items/${id}`, data))
}

export async function deleteDictItem(id: number): Promise<void> {
  await http.delete(`/dict/items/${id}`)
}

// ========== Extraction Methods ==========
export interface ExtractionMethod {
  id?: number
  name: string
  targetField?: string
  promptTemplate?: string
  isBuiltin?: boolean
  enabled?: boolean
  createdAt?: string
}

export async function listExtractionMethods(): Promise<ExtractionMethod[]> {
  return getData<ExtractionMethod[]>(await http.get<any>('/extraction-methods'))
}

export async function createExtractionMethod(data: Partial<ExtractionMethod>): Promise<ExtractionMethod> {
  return getData<ExtractionMethod>(await http.post<any>('/extraction-methods', data))
}

export async function updateExtractionMethod(id: number, data: Partial<ExtractionMethod>): Promise<ExtractionMethod> {
  return getData<ExtractionMethod>(await http.put<any>(`/extraction-methods/${id}`, data))
}

export async function deleteExtractionMethod(id: number): Promise<void> {
  await http.delete(`/extraction-methods/${id}`)
}

// ========== Comparison Methods ==========
export interface ComparisonMethod {
  id?: number
  name: string
  promptTemplate?: string
  isBuiltin?: boolean
  enabled?: boolean
  createdAt?: string
}

export async function listComparisonMethods(): Promise<ComparisonMethod[]> {
  return getData<ComparisonMethod[]>(await http.get<any>('/comparison-methods'))
}

export async function createComparisonMethod(data: Partial<ComparisonMethod>): Promise<ComparisonMethod> {
  return getData<ComparisonMethod>(await http.post<any>('/comparison-methods', data))
}

export async function updateComparisonMethod(id: number, data: Partial<ComparisonMethod>): Promise<ComparisonMethod> {
  return getData<ComparisonMethod>(await http.put<any>(`/comparison-methods/${id}`, data))
}

export async function deleteComparisonMethod(id: number): Promise<void> {
  await http.delete(`/comparison-methods/${id}`)
}

// ========== Trigger Rules ==========
export interface TriggerRule {
  id?: number
  name: string
  description?: string
  eventType?: string
  actionType?: string
  actionConfig?: string
  enabled?: boolean
  isBuiltin?: boolean
  createdAt?: string
}

export async function listTriggerRules(): Promise<TriggerRule[]> {
  return getData<TriggerRule[]>(await http.get<any>('/trigger-rules'))
}

export async function createTriggerRule(data: Partial<TriggerRule>): Promise<TriggerRule> {
  return getData<TriggerRule>(await http.post<any>('/trigger-rules', data))
}

export async function updateTriggerRule(id: number, data: Partial<TriggerRule>): Promise<TriggerRule> {
  return getData<TriggerRule>(await http.put<any>(`/trigger-rules/${id}`, data))
}

export async function deleteTriggerRule(id: number): Promise<void> {
  await http.delete(`/trigger-rules/${id}`)
}
