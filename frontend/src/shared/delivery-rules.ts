import type { DeliveryDetail, DeliveryRoute, User } from './types'
export function nextRouteAction(
  delivery: DeliveryDetail,
  route: DeliveryRoute,
  user: User,
): 'MOVING' | 'ARRIVED' | null {
  if (delivery.status === 'CANCELED' || delivery.status === 'DELIVERED') return null
  if (user.role !== 'MASTER' && route.managerId !== user.userId) return null
  if (route.status === 'MOVING') return 'ARRIVED'
  const previous = delivery.routes.find((r) => r.sequence === route.sequence - 1)
  if (route.status === 'WAITING' && (route.sequence === 1 || previous?.status === 'ARRIVED'))
    return 'MOVING'
  return null
}
