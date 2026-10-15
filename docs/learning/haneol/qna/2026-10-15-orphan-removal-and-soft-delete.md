---
title: 논리 삭제를 쓰는 애그리거트에서 orphanRemoval을 걸지 않는 이유
date: 2026-10-15
updated: 2026-10-15
asker: jihaneol
level: 중급
tags: [jpa, hibernate, cascade, orphan-removal, soft-delete]
---

# 논리 삭제를 쓰는 애그리거트에서 orphanRemoval을 걸지 않는 이유

## 질문
`Delivery.routes`(`@OneToMany`)에 `cascade = ALL`은 걸었는데 왜 `orphanRemoval = true`는 걸지 않았나.

## 짧은 답
orphanRemoval은 부모 컬렉션에서 빠진 자식을 DB에서 물리적으로 DELETE한다. 이 프로젝트는 물리 삭제를 금지하고 논리 삭제(`deleted_at`)만 쓴다. 배송 도메인에는 구간을 컬렉션에서 빼는 일도 없다. 그래서 걸 이유가 없고, 걸면 실수 한 번으로 이력이 사라지는 통로만 생긴다. 같은 이유로 `cascade = ALL`에 들어 있는 `REMOVE`도 덜어 내는 편이 맞다.

## 자세히

### orphanRemoval의 동작
```java
@OneToMany(mappedBy = "delivery", orphanRemoval = true)
private List<DeliveryRoute> routes;

delivery.getRoutes().remove(route); // 커밋 시 DELETE FROM p_delivery_routes WHERE delivery_route_id = ?
```
- 부모와의 관계가 끊긴 자식 = 고아로 보고 삭제한다.
- `cascade = REMOVE`는 부모를 `remove`할 때 자식도 지우는 것이고, orphanRemoval은 부모가 살아 있어도 관계가 끊긴 자식을 지우는 것이다.

### 논리 삭제와 맞지 않는 이유
- 논리 삭제는 `UPDATE ... SET deleted_at = now()`다. JPA 입장에서는 삭제가 아니라 필드 변경이다.
- 그래서 cascade REMOVE와 orphanRemoval은 논리 삭제에 전파되지 않는다. 반대로 둘이 실행되면 논리 삭제 규칙을 건너뛰고 물리 DELETE가 나간다.
- 부모를 논리 삭제할 때 자식도 지우려면 애그리거트 메서드에서 직접 자식의 `softDelete()`를 부른다.

### cascade는 무엇만 남기나
- 애그리거트 루트를 저장할 때 자식도 같이 저장되어야 하므로 `PERSIST`는 필요하다.
- `REMOVE`는 물리 삭제를 전파하므로 논리 삭제 정책과 맞지 않는다.
- 나머지(`MERGE`, `DETACH`, `REFRESH`)는 실제로 쓰는 경로가 있을 때만 추가한다.

## 이 프로젝트에서는
- `docs/agents/rules/repository.md`: 물리 삭제(`delete`, `deleteById`)를 쓰지 않는다.
- `common/.../BaseEntity.softDelete(username)`: 논리 삭제.
- `delivery-service/.../domain/Delivery.java`의 `routes`: 구간은 생성 시 2~3개 만들어지고 이후 상태만 바뀐다. 취소는 `CANCELED`, I-13 삭제는 논리 삭제다. 배송을 논리 삭제할 때 구간도 함께 `softDelete()` 해야 한다.

## 면접 포인트
- cascade REMOVE와 orphanRemoval의 차이: 부모 삭제 전파 vs 관계가 끊긴 자식 삭제.
- 논리 삭제를 쓰면 JPA의 삭제 전파 기능이 동작하지 않는 이유와, 그때 애그리거트에서 무엇을 직접 해야 하는지.
