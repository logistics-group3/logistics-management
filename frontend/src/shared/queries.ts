import { useQuery } from '@tanstack/react-query'
import { api } from './api'
import type { Company, Hub, Page, User } from './types'
export async function allPages<T>(path: string, signal: AbortSignal) {
  const separator = path.includes('?') ? '&' : '?'
  const first = await api<Page<T>>(`${path}${separator}size=50&page=0`, { signal })
  const items = [...first.content]
  for (let page = 1; page < first.totalPages; page++)
    items.push(
      ...(await api<Page<T>>(`${path}${separator}size=50&page=${page}`, { signal })).content,
    )
  return items
}
export function useDirectory() {
  const companies = useQuery({
    queryKey: ['directory', 'companies'],
    queryFn: ({ signal }) => allPages<Company>('/companies', signal),
    staleTime: 60000,
  })
  const hubs = useQuery({
    queryKey: ['directory', 'hubs'],
    queryFn: ({ signal }) => allPages<Hub>('/hubs', signal),
    staleTime: 300000,
  })
  return {
    companies,
    hubs,
    companyName: (id: string) =>
      companies.data?.find((c) => c.companyId === id)?.name ?? id?.slice(0, 8) ?? '—',
    hubName: (id: string) => hubs.data?.find((h) => h.hubId === id)?.name ?? id?.slice(0, 8) ?? '—',
  }
}

export function useUsersDirectory(enabled: boolean) {
  return useQuery({
    queryKey: ['users', 'directory'],
    queryFn: ({ signal }) => allPages<User>('/users', signal),
    enabled,
    staleTime: 30000,
  })
}
