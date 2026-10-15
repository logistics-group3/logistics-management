export const money = (value: string | number | bigint) =>
  `${BigInt(value).toLocaleString('ko-KR')}원`
export const shortId = (value: string) => value?.slice(0, 8).toUpperCase() ?? '—'
export const dateTime = (value: string) =>
  value
    ? new Intl.DateTimeFormat('ko-KR', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(new Date(value))
    : '—'
export const localDate = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul' }).format(new Date())
export const labels: Record<string, string> = {
  ORDERED: '주문 완료',
  CANCELED: '취소',
  PICKUP_WAITING: '집하 대기',
  PICKING_UP: '집하 중',
  HUB_WAITING: '허브 대기',
  HUB_MOVING: '허브 이동 중',
  DEST_HUB_ARRIVED: '도착 허브 입고',
  OUT_FOR_DELIVERY: '업체 배송 중',
  DELIVERED: '배송 완료',
  WAITING: '대기',
  MOVING: '이동 중',
  ARRIVED: '도착',
  PICKUP: '공급업체 집하',
  HUB_TRANSFER: '허브 간 이동',
  LAST_MILE: '수령업체 배송',
  MASTER: '관리자',
  USER: '일반 사용자',
  COMPANY: '업체',
  HUB: '허브',
}
