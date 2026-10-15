export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  sort: string | null
}
export interface User {
  userId: string
  username: string
  name: string
  role: 'MASTER' | 'USER'
  isDeliveryManager: boolean
  hubId: string | null
  affiliationType: string
  affiliationName: string
  slackId: string
  createdAt: string
}
export interface Hub {
  hubId: string
  name: string
  address: string
  latitude: number
  longitude: number
}
export interface Company {
  companyId: string
  name: string
  hubId: string
  address: string
  managerId: string
  createdAt: string
}
export interface Product {
  productId: string
  companyId: string
  hubId: string
  name: string
  description: string | null
  price: string
  createdAt: string
}
export interface OrderInput {
  supplierCompanyId: string
  receiverCompanyId: string
  productId: string
  quantity: number
  requestNote: string
}
export interface Order extends OrderInput {
  orderId: string
  ordererId: string
  productName: string
  unitPrice: string
  totalPrice: string
  status: 'ORDERED' | 'CANCELED'
  deliveryId: string
  createdAt: string
  updatedAt: string
}
export type DeliveryStatus =
  | 'PICKUP_WAITING'
  | 'PICKING_UP'
  | 'HUB_WAITING'
  | 'HUB_MOVING'
  | 'DEST_HUB_ARRIVED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'CANCELED'
export interface Delivery {
  deliveryId: string
  orderId: string
  status: DeliveryStatus
  supplierCompanyId: string
  receiverCompanyId: string
  departureHubId: string
  destinationHubId: string
  pickupAddress: string
  deliveryAddress: string
  recipientName: string
  createdAt: string
  updatedAt: string
}
export interface DeliveryRoute {
  deliveryRouteId: string
  sequence: number
  routeType: 'PICKUP' | 'HUB_TRANSFER' | 'LAST_MILE'
  departureHubId: string | null
  arrivalHubId: string | null
  expectedDistanceKm: number | null
  expectedDurationMinutes: number | null
  actualDistanceKm: number | null
  actualDurationMinutes: number | null
  status: 'WAITING' | 'MOVING' | 'ARRIVED' | 'CANCELED'
  managerId: string
}
export interface DeliveryDetail extends Delivery {
  routes: DeliveryRoute[]
}
