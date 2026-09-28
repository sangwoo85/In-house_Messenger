import { http } from '@/services/http'
import type { DirectoryUser } from './users.api'

/** 업무 시스템에서 동기화한 부서. parentId가 없으면 최상위 조직이다. */
export interface Department {
  id: string
  parentId: string | null
  name: string
}

/** stale은 업무 API 장애로 마지막 성공한 조직 정보를 사용 중임을 나타낸다. */
export interface OrganizationDirectory {
  departments: Department[]
  users: DirectoryUser[]
  syncedAt: string | null
  stale: boolean
}

/** 메신저 서버를 통해 계층형 조직과 동기화 상태를 조회한다. */
export async function getOrganizations(): Promise<OrganizationDirectory> {
  return (await http.get<{ data: OrganizationDirectory }>('/organizations')).data.data
}
