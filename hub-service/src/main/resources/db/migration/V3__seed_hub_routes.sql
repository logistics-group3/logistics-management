-- SA 10.1 허브 경로 272건(17 × 16, 방향별) 초기 데이터. 경로는 조회 전용 메타 데이터라 마이그레이션으로 한 번만 적재한다.
-- 허브 좌표(V2)로 계산한 값을 고정했다. hub_route_id도 다른 서비스가 참조할 수 있으므로 값을 고정한다.
--   이동거리(km) = 두 허브 좌표의 Haversine 직선거리(지구 반지름 6371km) × 도로 보정계수 1.3, 소수 둘째 자리 반올림
--   소요시간(분) = 반올림한 이동거리 ÷ 평균 시속 70km × 60, 분 단위 올림
-- 보정계수·시속·좌표를 바꾸려면 이 파일을 고치지 말고 새 마이그레이션으로 UPDATE한다. 값 검증은 HubRouteSeedIntegrationTest가 같은 공식을 SQL로 다시 계산해 대조한다.
INSERT INTO p_hub_routes (hub_route_id, departure_hub_id, arrival_hub_id, distance_km, duration_minutes, created_at, created_by, updated_at, updated_by)
VALUES ('15aede10-24de-4a74-83b3-e4ffaa76c678', '6f39d519-4947-4a95-a454-58c25728f388', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 36.12, 31, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 경기 북부 센터
       ('c86540c2-0cdf-4052-8e34-8d72bab135e2', '6f39d519-4947-4a95-a454-58c25728f388', '4944c848-671b-4038-a69e-1788b65e9f71', 52.13, 45, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 경기 남부 센터
       ('6f458136-d098-4e5f-9530-df6fb9d30dd7', '6f39d519-4947-4a95-a454-58c25728f388', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 408.57, 351, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 부산광역시 센터
       ('a42947e8-a3b4-425d-b916-d264708bc310', '6f39d519-4947-4a95-a454-58c25728f388', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 288.77, 248, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 대구광역시 센터
       ('e44cc480-8db9-4b75-a24c-7a73b8edc579', '6f39d519-4947-4a95-a454-58c25728f388', '6153ad9f-7a1b-4616-b281-078a83484de5', 48.07, 42, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 인천광역시 센터
       ('fb5c7a89-9ab0-483e-9b88-73d2ff808a21', '6f39d519-4947-4a95-a454-58c25728f388', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 337.79, 290, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 광주광역시 센터
       ('d91407c5-a6c5-4cfb-b7a2-f2627df6f5a2', '6f39d519-4947-4a95-a454-58c25728f388', '93eb97ce-b201-46e0-b26b-584c39191aa9', 167.01, 144, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 대전광역시 센터
       ('53299ea6-ef11-4923-9c95-aec69d72bcac', '6f39d519-4947-4a95-a454-58c25728f388', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 380.56, 327, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 울산광역시 센터
       ('c7f5062a-c34d-428e-b1d0-d85a77bafbca', '6f39d519-4947-4a95-a454-58c25728f388', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 146.77, 126, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 세종특별자치시 센터
       ('92cc2abd-921e-4639-bb20-c7d54ace7ff8', '6f39d519-4947-4a95-a454-58c25728f388', '6cc4c486-6f2f-4a68-86de-b21173703f07', 88.55, 76, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 강원특별자치도 센터
       ('0e9fd951-cf99-483f-af01-0aaf4063aa4d', '6f39d519-4947-4a95-a454-58c25728f388', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 130.21, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 충청북도 센터
       ('204acac8-5da8-4d65-a3bb-87abdb04d63b', '6f39d519-4947-4a95-a454-58c25728f388', '6090face-fa49-4edb-a089-1bd667aeaf0f', 129.81, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 충청남도 센터
       ('2775d7ed-a1cc-46cc-aab9-e9d34aa11dc9', '6f39d519-4947-4a95-a454-58c25728f388', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 240.92, 207, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 전북특별자치도 센터
       ('2e0c7223-5aa9-443b-b737-ecdd9ade93b8', '6f39d519-4947-4a95-a454-58c25728f388', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 393.63, 338, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 전라남도 센터
       ('f8dcbaf4-d93f-4d71-8e72-10583167ca9e', '6f39d519-4947-4a95-a454-58c25728f388', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 206.91, 178, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 경상북도 센터
       ('9ff961ac-c456-46ac-8852-b6cf3eda6048', '6f39d519-4947-4a95-a454-58c25728f388', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 372.88, 320, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 서울특별시 센터 -> 경상남도 센터
       ('25e18a52-3916-427a-baae-b2a2d76a003b', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '6f39d519-4947-4a95-a454-58c25728f388', 36.12, 31, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 서울특별시 센터
       ('1728a554-8e94-426f-9f39-ad7257ce1e50', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '4944c848-671b-4038-a69e-1788b65e9f71', 87.23, 75, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 경기 남부 센터
       ('38a46fde-1650-4e96-862f-953b4b4d6330', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 443.02, 380, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 부산광역시 센터
       ('a7469311-3506-4fad-96f1-4f7ec23f307d', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 323.76, 278, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 대구광역시 센터
       ('d9659f2e-b1fc-495a-8dda-2b4cc661e71b', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '6153ad9f-7a1b-4616-b281-078a83484de5', 32.90, 29, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 인천광역시 센터
       ('b42fd786-0a1e-4b78-86ea-8c7e1604a0aa', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 358.54, 308, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 광주광역시 센터
       ('37984330-e996-4ce4-a401-ec0d8a9cee9c', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '93eb97ce-b201-46e0-b26b-584c39191aa9', 195.56, 168, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 대전광역시 센터
       ('a0998dd5-5f61-411a-aec1-d61c4d54498a', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 416.25, 357, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 울산광역시 센터
       ('e7c80676-8288-4f48-b5cc-991824b6c215', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 174.42, 150, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 세종특별자치시 센터
       ('57dfaf05-8fd8-4435-b99a-7c652d757dba', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '6cc4c486-6f2f-4a68-86de-b21173703f07', 102.54, 88, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 강원특별자치도 센터
       ('f74adb77-bdba-4072-8bff-4cc27c0104ae', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 161.76, 139, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 충청북도 센터
       ('b902c878-b1b9-462b-84bd-66773d80b863', 'cfa35654-5a89-49e3-a6af-1d538093a49a', '6090face-fa49-4edb-a089-1bd667aeaf0f', 143.72, 124, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 충청남도 센터
       ('3daaa829-b985-478f-ac1b-774f640b0c2b', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 264.52, 227, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 전북특별자치도 센터
       ('0d5f5535-fe6a-4705-bf9f-bbb3a1012cee', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 411.05, 353, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 전라남도 센터
       ('db6848ff-ebd9-404f-a95f-7a4589b01c17', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 243.03, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 경상북도 센터
       ('9be779b2-fb95-402e-99f8-5b2194f6c7a6', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 406.56, 349, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 북부 센터 -> 경상남도 센터
       ('36e7cef2-7505-45a6-be6d-12708304d764', '4944c848-671b-4038-a69e-1788b65e9f71', '6f39d519-4947-4a95-a454-58c25728f388', 52.13, 45, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 서울특별시 센터
       ('873508f4-f87c-4415-860a-04e4652eae41', '4944c848-671b-4038-a69e-1788b65e9f71', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 87.23, 75, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 경기 북부 센터
       ('44bea284-ff77-4dff-b610-54346d57bd7c', '4944c848-671b-4038-a69e-1788b65e9f71', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 356.44, 306, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 부산광역시 센터
       ('969d167e-43b0-4a7c-9665-cecd38f935d2', '4944c848-671b-4038-a69e-1788b65e9f71', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 236.70, 203, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 대구광역시 센터
       ('ef45e848-0f3e-40f2-8834-1066d3f7261d', '4944c848-671b-4038-a69e-1788b65e9f71', '6153ad9f-7a1b-4616-b281-078a83484de5', 86.41, 75, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 인천광역시 센터
       ('4a81eb97-71b5-4b03-92a2-3f6be443bdbc', '4944c848-671b-4038-a69e-1788b65e9f71', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 299.55, 257, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 광주광역시 센터
       ('ca1f88f4-cbbb-4eff-933d-040efce980fb', '4944c848-671b-4038-a69e-1788b65e9f71', '93eb97ce-b201-46e0-b26b-584c39191aa9', 121.12, 104, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 대전광역시 센터
       ('43d1ca00-a506-47d4-86ad-562f2d205a26', '4944c848-671b-4038-a69e-1788b65e9f71', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 329.08, 283, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 울산광역시 센터
       ('9b29e027-30bb-42a8-b9bc-6e0c2d6bfd74', '4944c848-671b-4038-a69e-1788b65e9f71', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 102.90, 89, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 세종특별자치시 센터
       ('e76fd802-2a65-426a-b1d4-9caaec5e38dd', '4944c848-671b-4038-a69e-1788b65e9f71', '6cc4c486-6f2f-4a68-86de-b21173703f07', 106.87, 92, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 강원특별자치도 센터
       ('cc34e9ed-779c-4b5c-b493-1330445c60d9', '4944c848-671b-4038-a69e-1788b65e9f71', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 81.01, 70, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 충청북도 센터
       ('dc220620-92d2-415a-ac4e-ddbb90751c88', '4944c848-671b-4038-a69e-1788b65e9f71', '6090face-fa49-4edb-a089-1bd667aeaf0f', 110.27, 95, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 충청남도 센터
       ('b4676fdd-b43e-47c4-aa4c-13e284071a73', '4944c848-671b-4038-a69e-1788b65e9f71', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 200.26, 172, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 전북특별자치도 센터
       ('dcc9340d-1800-41b2-a96d-4618d71a5f6e', '4944c848-671b-4038-a69e-1788b65e9f71', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 359.19, 308, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 전라남도 센터
       ('c52f3be4-8011-40f1-8ed4-dddfbf01cd69', '4944c848-671b-4038-a69e-1788b65e9f71', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 157.66, 136, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 경상북도 센터
       ('26377e31-bc9f-422d-91e2-e223f89d3647', '4944c848-671b-4038-a69e-1788b65e9f71', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 320.93, 276, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경기 남부 센터 -> 경상남도 센터
       ('0319f3e4-6299-497e-9035-e9af46f258e0', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '6f39d519-4947-4a95-a454-58c25728f388', 408.57, 351, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 서울특별시 센터
       ('08398c31-4dae-400e-b6a3-945f8661fe1b', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 443.02, 380, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 경기 북부 센터
       ('afa50c38-fa1d-41eb-ba5e-d053907f0107', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '4944c848-671b-4038-a69e-1788b65e9f71', 356.44, 306, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 경기 남부 센터
       ('121b18a3-278c-4ec5-bf1b-53692831caf6', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 121.08, 104, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 대구광역시 센터
       ('d088f6e5-8470-417b-9270-d97ace85391b', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '6153ad9f-7a1b-4616-b281-078a83484de5', 433.65, 372, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 인천광역시 센터
       ('92cb75fd-9f13-412c-a443-efcf73b321c1', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 259.12, 223, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 광주광역시 센터
       ('a43ee0f5-980a-4f45-9f36-30484084439f', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '93eb97ce-b201-46e0-b26b-584c39191aa9', 263.46, 226, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 대전광역시 센터
       ('91054029-d38c-4f96-94e9-f73a29111005', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 67.36, 58, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 울산광역시 센터
       ('50559991-0f18-472f-aaf9-f5631697b745', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 284.35, 244, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 세종특별자치시 센터
       ('1656a66a-e861-40ea-b835-f1d10f852b59', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '6cc4c486-6f2f-4a68-86de-b21173703f07', 426.78, 366, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 강원특별자치도 센터
       ('3224b024-f49d-486c-8f7d-f5c86e458579', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 284.44, 244, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 충청북도 센터
       ('330e467f-d1be-48f1-b364-88c5d4ce80cf', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', '6090face-fa49-4edb-a089-1bd667aeaf0f', 353.48, 303, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 충청남도 센터
       ('15a816cb-dd5a-4582-9a84-5121651e7356', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 249.19, 214, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 전북특별자치도 센터
       ('cb777285-7981-4049-9c69-e44a64994115', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 308.74, 265, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 전라남도 센터
       ('69f7b243-d5e2-4b79-bf93-9d620036437a', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 219.40, 189, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 경상북도 센터
       ('0d493272-b9c1-42e3-9b4c-b971420b241a', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 44.78, 39, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 부산광역시 센터 -> 경상남도 센터
       ('51c6c674-454c-489b-ba8c-148897f3b461', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '6f39d519-4947-4a95-a454-58c25728f388', 288.77, 248, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 서울특별시 센터
       ('ab4b80d9-892e-4668-bca7-a817a773ed1a', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 323.76, 278, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 경기 북부 센터
       ('cd0aa839-8ae5-45a7-9d60-75a61f46219c', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '4944c848-671b-4038-a69e-1788b65e9f71', 236.70, 203, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 경기 남부 센터
       ('bfa4021c-b182-47a5-89bd-1b142026f156', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 121.08, 104, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 부산광역시 센터
       ('1de269cb-e2f8-473b-86dd-0a87673a1fc8', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '6153ad9f-7a1b-4616-b281-078a83484de5', 316.59, 272, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 인천광역시 센터
       ('faed0d45-f9ce-4315-8485-4f55db89f94d', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 229.87, 198, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 광주광역시 센터
       ('5c8e94b7-c02f-457a-b628-017ab174e8a1', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '93eb97ce-b201-46e0-b26b-584c39191aa9', 157.19, 135, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 대전광역시 센터
       ('c1d16ee5-9ccf-44c1-9271-1c81b559a436', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 98.08, 85, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 울산광역시 센터
       ('175a9447-7cfc-4a53-a2cc-063d3183fc21', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 175.74, 151, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 세종특별자치시 센터
       ('fdf862e9-c332-4b75-a8d6-883fd8cc7aa1', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '6cc4c486-6f2f-4a68-86de-b21173703f07', 306.31, 263, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 강원특별자치도 센터
       ('0dd79f3b-69ef-4eeb-badc-85d00e0fdeb6', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 169.19, 146, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 충청북도 센터
       ('44042363-1782-4dfa-8fe0-bd563f907b83', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', '6090face-fa49-4edb-a089-1bd667aeaf0f', 248.82, 214, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 충청남도 센터
       ('376daa3b-c46a-4d61-b4d4-d83ce6fb61ae', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 174.55, 150, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 전북특별자치도 센터
       ('d4a4f7a2-2039-4878-894f-cd8af8da65d9', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 294.44, 253, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 전라남도 센터
       ('7da670cd-d9b2-4d35-af0c-8ae04fee7c02', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 101.65, 88, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 경상북도 센터
       ('68d05742-f559-4c6c-895d-719a0e189ef4', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 92.93, 80, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대구광역시 센터 -> 경상남도 센터
       ('138d90f5-823f-45ea-8e74-6470c97dcdb0', '6153ad9f-7a1b-4616-b281-078a83484de5', '6f39d519-4947-4a95-a454-58c25728f388', 48.07, 42, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 서울특별시 센터
       ('597d44eb-cfe6-4ab2-90fe-1900aaad19a3', '6153ad9f-7a1b-4616-b281-078a83484de5', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 32.90, 29, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 경기 북부 센터
       ('6303a44c-055e-4293-a082-53065d58ace2', '6153ad9f-7a1b-4616-b281-078a83484de5', '4944c848-671b-4038-a69e-1788b65e9f71', 86.41, 75, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 경기 남부 센터
       ('c11a1efd-772c-4e2f-92ea-d6c40fcccfe6', '6153ad9f-7a1b-4616-b281-078a83484de5', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 433.65, 372, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 부산광역시 센터
       ('bba41a3a-e7ec-4465-bf41-0cac2d985416', '6153ad9f-7a1b-4616-b281-078a83484de5', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 316.59, 272, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 대구광역시 센터
       ('91152710-7974-4099-a29b-d4adbc13a7c9', '6153ad9f-7a1b-4616-b281-078a83484de5', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 332.33, 285, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 광주광역시 센터
       ('f53cfcb6-2d29-4a74-8f79-73b162a9230d', '6153ad9f-7a1b-4616-b281-078a83484de5', '93eb97ce-b201-46e0-b26b-584c39191aa9', 178.08, 153, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 대전광역시 센터
       ('969a4e4a-278a-425b-ade0-2d465f2f2c6c', '6153ad9f-7a1b-4616-b281-078a83484de5', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 411.62, 353, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 울산광역시 센터
       ('9e8b9e29-fdaa-4dee-8154-5f0d4b5889d0', '6153ad9f-7a1b-4616-b281-078a83484de5', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 156.36, 135, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 세종특별자치시 센터
       ('3bef7151-bd96-4e39-aaaf-a59abe6c1278', '6153ad9f-7a1b-4616-b281-078a83484de5', '6cc4c486-6f2f-4a68-86de-b21173703f07', 131.03, 113, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 강원특별자치도 센터
       ('33c32a59-77e0-41ce-8b4d-8f9c974f7d1f', '6153ad9f-7a1b-4616-b281-078a83484de5', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 149.35, 129, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 충청북도 센터
       ('4452ab7a-e604-497d-84a3-b6bdb64f62fc', '6153ad9f-7a1b-4616-b281-078a83484de5', '6090face-fa49-4edb-a089-1bd667aeaf0f', 115.59, 100, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 충청남도 센터
       ('c66cdb4a-95c0-4d32-86ed-71e254a3ef44', '6153ad9f-7a1b-4616-b281-078a83484de5', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 241.06, 207, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 전북특별자치도 센터
       ('14cc1fdf-fa6a-4c9e-aac9-b896b30a1e8a', '6153ad9f-7a1b-4616-b281-078a83484de5', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 382.67, 329, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 전라남도 센터
       ('237157d3-bc9e-4def-a5e6-2a4da3f37ee2', '6153ad9f-7a1b-4616-b281-078a83484de5', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 243.65, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 경상북도 센터
       ('7e8fab9a-2363-4caf-b120-f9c1841ab221', '6153ad9f-7a1b-4616-b281-078a83484de5', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 395.35, 339, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 인천광역시 센터 -> 경상남도 센터
       ('0b0bea4a-066d-4bf5-bfbd-30edfd2f5d82', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '6f39d519-4947-4a95-a454-58c25728f388', 337.79, 290, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 서울특별시 센터
       ('2e510ee5-28cd-43ad-bcab-bb575c77a1c5', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 358.54, 308, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 경기 북부 센터
       ('56fc4cff-6727-4bad-a5f1-eaabdf4f0647', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '4944c848-671b-4038-a69e-1788b65e9f71', 299.55, 257, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 경기 남부 센터
       ('7b3add9c-6c91-4838-acb8-e905aca044d9', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 259.12, 223, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 부산광역시 센터
       ('5b971cd4-5b7c-4a86-a229-fb615376cdbe', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 229.87, 198, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 대구광역시 센터
       ('9d0e37f9-57ba-4a16-8b38-5ac9ae31dd95', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '6153ad9f-7a1b-4616-b281-078a83484de5', 332.33, 285, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 인천광역시 센터
       ('6e89b04a-7c2e-4a1a-8352-33d04601b51a', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '93eb97ce-b201-46e0-b26b-584c39191aa9', 183.11, 157, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 대전광역시 센터
       ('89b88a6a-a89f-45c4-aa93-eedd9038d500', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 295.45, 254, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 울산광역시 센터
       ('8231a373-ac13-4947-8054-0e65b7d5c73e', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 197.58, 170, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 세종특별자치시 센터
       ('292d8912-d09a-48db-88cf-f928d39d83ce', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '6cc4c486-6f2f-4a68-86de-b21173703f07', 405.33, 348, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 강원특별자치도 센터
       ('9cccce99-d846-43e2-a3a2-fb1e8bd4196d', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 226.06, 194, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 충청북도 센터
       ('28c2b901-61a6-4df4-bde6-7541f586b50e', '92db1fc2-984c-4d06-8ad9-6d28169c0404', '6090face-fa49-4edb-a089-1bd667aeaf0f', 217.11, 187, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 충청남도 센터
       ('d1e18e6f-948e-4371-9a98-9aa240b1728f', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 100.06, 86, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 전북특별자치도 센터
       ('f9f9a958-5407-40fb-9a1b-c7a5550a6830', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 67.70, 59, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 전라남도 센터
       ('aa46d7ab-efb4-416e-b2ec-f520d8fe8723', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 281.74, 242, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 경상북도 센터
       ('18d4b4df-15a3-4893-af93-3a458b8e6162', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 217.68, 187, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 광주광역시 센터 -> 경상남도 센터
       ('10ac855c-2baf-44b7-9e5a-6d6cec87b349', '93eb97ce-b201-46e0-b26b-584c39191aa9', '6f39d519-4947-4a95-a454-58c25728f388', 167.01, 144, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 서울특별시 센터
       ('3943057b-27f4-497f-8dfc-4373ebe2847e', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 195.56, 168, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 경기 북부 센터
       ('898cfe5e-3db1-4038-aea1-0e8bcdd2c817', '93eb97ce-b201-46e0-b26b-584c39191aa9', '4944c848-671b-4038-a69e-1788b65e9f71', 121.12, 104, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 경기 남부 센터
       ('8a758445-7639-4996-9f33-e6dabd75bcc5', '93eb97ce-b201-46e0-b26b-584c39191aa9', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 263.46, 226, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 부산광역시 센터
       ('77cc81e1-f97c-42e9-b8e5-086c8db3184e', '93eb97ce-b201-46e0-b26b-584c39191aa9', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 157.19, 135, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 대구광역시 센터
       ('d40fc135-bba4-47c2-adb0-963e3d551850', '93eb97ce-b201-46e0-b26b-584c39191aa9', '6153ad9f-7a1b-4616-b281-078a83484de5', 178.08, 153, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 인천광역시 센터
       ('f433c7df-ddcd-4ec9-a6f4-24737796ec2c', '93eb97ce-b201-46e0-b26b-584c39191aa9', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 183.11, 157, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 광주광역시 센터
       ('10c6437d-4d26-49e1-bd02-e59f0a072ab5', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 255.11, 219, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 울산광역시 센터
       ('deaee2b5-56ac-46f9-a55b-453976871dee', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 21.81, 19, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 세종특별자치시 센터
       ('817fab89-9fae-439c-8ba0-0164707d8d6a', '93eb97ce-b201-46e0-b26b-584c39191aa9', '6cc4c486-6f2f-4a68-86de-b21173703f07', 223.85, 192, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 강원특별자치도 센터
       ('a3fc57fa-dc77-4b4b-a39c-6f65296dcd14', '93eb97ce-b201-46e0-b26b-584c39191aa9', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 43.01, 37, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 충청북도 센터
       ('d232a116-bddb-4b6b-b1b6-5f49e03b33d7', '93eb97ce-b201-46e0-b26b-584c39191aa9', '6090face-fa49-4edb-a089-1bd667aeaf0f', 91.74, 79, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 충청남도 센터
       ('81dfe957-f116-47d9-909c-506f221bcabf', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 83.23, 72, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 전북특별자치도 센터
       ('d8c839e2-47c3-4b5e-a031-bc24f67dc309', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 246.85, 212, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 전라남도 센터
       ('205b2cd4-3e0c-4970-a797-e5439ed4ebca', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 134.22, 116, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 경상북도 센터
       ('898c5501-afa0-413e-9b91-71809930daac', '93eb97ce-b201-46e0-b26b-584c39191aa9', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 222.15, 191, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 대전광역시 센터 -> 경상남도 센터
       ('b99137f2-fb4f-4bde-9942-356e5c32f7f7', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '6f39d519-4947-4a95-a454-58c25728f388', 380.56, 327, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 서울특별시 센터
       ('182ebd48-55f9-45a7-8023-a73ecab1c268', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 416.25, 357, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 경기 북부 센터
       ('39d551bb-9ead-4e55-89a7-6d90a428ba02', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '4944c848-671b-4038-a69e-1788b65e9f71', 329.08, 283, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 경기 남부 센터
       ('5abf2a8a-1f05-413a-b370-159d78496c5b', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 67.36, 58, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 부산광역시 센터
       ('590815c5-8b0a-4f06-8d87-6988d22dbcba', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 98.08, 85, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 대구광역시 센터
       ('9fe9c114-8fa6-4ec1-b48e-cdb1ae473e52', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '6153ad9f-7a1b-4616-b281-078a83484de5', 411.62, 353, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 인천광역시 센터
       ('1762e529-e5d9-4712-9fde-3d04cd388097', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 295.45, 254, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 광주광역시 센터
       ('2b63acce-8c48-4ab1-be25-8f59196001f0', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '93eb97ce-b201-46e0-b26b-584c39191aa9', 255.11, 219, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 대전광역시 센터
       ('e0fd1588-17b7-4c01-a1c1-8f88d327b8e0', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 273.83, 235, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 세종특별자치시 센터
       ('02aca2bb-07fc-4c37-a9fe-39a55f9053b5', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '6cc4c486-6f2f-4a68-86de-b21173703f07', 386.08, 331, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 강원특별자치도 센터
       ('8b8c715e-d222-40b5-aa02-30979f5a619e', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 266.24, 229, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 충청북도 센터
       ('da2ac984-5707-4376-acc7-821666afb1d3', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', '6090face-fa49-4edb-a089-1bd667aeaf0f', 346.82, 298, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 충청남도 센터
       ('d6e6dbdc-929c-48cf-8636-17c1ed6a04e6', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 262.58, 226, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 전북특별자치도 센터
       ('dd1f0b27-da5e-40f1-a4f4-105c0fa7d29c', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 352.49, 303, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 전라남도 센터
       ('92747208-3050-44e1-85d6-af7ff62a7ad4', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 178.21, 153, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 경상북도 센터
       ('2862d60d-ffeb-463d-9ee3-b72c26fc412f', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 84.86, 73, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 울산광역시 센터 -> 경상남도 센터
       ('61f338ba-e37f-4e5d-9ef6-bc17de090641', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '6f39d519-4947-4a95-a454-58c25728f388', 146.77, 126, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 서울특별시 센터
       ('bf6dfdae-206d-4160-836d-4ee789a07da2', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 174.42, 150, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 경기 북부 센터
       ('a865db1b-6ad0-4e2f-b7dc-a230b5caec53', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '4944c848-671b-4038-a69e-1788b65e9f71', 102.90, 89, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 경기 남부 센터
       ('ae3ce103-7d68-4063-b9f6-f06589b3c236', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 284.35, 244, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 부산광역시 센터
       ('baa84d67-e4ec-48be-9c6b-854d1ff64703', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 175.74, 151, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 대구광역시 센터
       ('500d58b2-db1c-467b-8c32-3e6e030a76fc', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '6153ad9f-7a1b-4616-b281-078a83484de5', 156.36, 135, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 인천광역시 센터
       ('edd412a0-06d5-4350-8172-6118dc2eb406', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 197.58, 170, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 광주광역시 센터
       ('63d2b64b-b649-415e-910b-6236aa4f745d', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '93eb97ce-b201-46e0-b26b-584c39191aa9', 21.81, 19, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 대전광역시 센터
       ('71cbb329-c683-4e73-b197-c67f64d8f0ef', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 273.83, 235, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 울산광역시 센터
       ('12f971f0-404b-4380-aec5-10fc409cfe24', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '6cc4c486-6f2f-4a68-86de-b21173703f07', 207.79, 179, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 강원특별자치도 센터
       ('63dc5ca3-d9b7-4b83-80cc-983d21cc4857', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 32.53, 28, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 충청북도 센터
       ('b158402c-b1d8-4bd4-a583-402ec00df6e2', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', '6090face-fa49-4edb-a089-1bd667aeaf0f', 73.71, 64, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 충청남도 센터
       ('b97cd7c2-3127-4c0a-b5d7-36c8a9de8d7b', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 97.72, 84, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 전북특별자치도 센터
       ('da11085d-7b95-4765-a1f2-f41bbf942e13', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 259.34, 223, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 전라남도 센터
       ('c7fb5a91-5729-43ed-87c7-784f6996a925', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 141.94, 122, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 경상북도 센터
       ('9c9413e0-417d-4a4c-92e1-aefa1d4d7deb', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 243.43, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 세종특별자치시 센터 -> 경상남도 센터
       ('edb67475-54da-4d51-a706-db5184d6a54f', '6cc4c486-6f2f-4a68-86de-b21173703f07', '6f39d519-4947-4a95-a454-58c25728f388', 88.55, 76, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 서울특별시 센터
       ('89fd97a2-8c70-41a9-a5ad-5fa567682ed8', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 102.54, 88, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 경기 북부 센터
       ('e72d4df3-fdc6-44ff-813a-8e0f9606161a', '6cc4c486-6f2f-4a68-86de-b21173703f07', '4944c848-671b-4038-a69e-1788b65e9f71', 106.87, 92, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 경기 남부 센터
       ('32bb6f97-a6c0-436c-8a44-c20ba9fc8201', '6cc4c486-6f2f-4a68-86de-b21173703f07', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 426.78, 366, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 부산광역시 센터
       ('ecd0ce7f-7751-43f8-adc3-a92861be1b6b', '6cc4c486-6f2f-4a68-86de-b21173703f07', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 306.31, 263, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 대구광역시 센터
       ('8daf4c36-1149-486d-a52e-6d78eadffba0', '6cc4c486-6f2f-4a68-86de-b21173703f07', '6153ad9f-7a1b-4616-b281-078a83484de5', 131.03, 113, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 인천광역시 센터
       ('83f13126-1343-45ec-a049-b5ac50ab76ae', '6cc4c486-6f2f-4a68-86de-b21173703f07', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 405.33, 348, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 광주광역시 센터
       ('3a07ceeb-8f86-43e4-895a-51d6910e6bf4', '6cc4c486-6f2f-4a68-86de-b21173703f07', '93eb97ce-b201-46e0-b26b-584c39191aa9', 223.85, 192, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 대전광역시 센터
       ('482c298a-bd76-40ea-833b-0056e5ca4ba3', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 386.08, 331, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 울산광역시 센터
       ('a2dcd088-f5bb-4f5e-847c-7220d09a0c9c', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 207.79, 179, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 세종특별자치시 센터
       ('88ef7fd8-6cbb-477b-b3db-bcfc93c0404c', '6cc4c486-6f2f-4a68-86de-b21173703f07', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 181.23, 156, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 충청북도 센터
       ('2f78eb72-f322-417d-935c-a18a3ac3531d', '6cc4c486-6f2f-4a68-86de-b21173703f07', '6090face-fa49-4edb-a089-1bd667aeaf0f', 212.18, 182, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 충청남도 센터
       ('8c3b09f1-be1b-4146-aaa7-64d58889ee77', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 305.49, 262, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 전북특별자치도 센터
       ('961aadab-8a64-4a0c-9eb2-146c4d00e8f6', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 465.86, 400, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 전라남도 센터
       ('27f8f0c8-5669-4f35-a4e0-baf4b8ca29a7', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 208.57, 179, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 경상북도 센터
       ('5a694e87-ba49-435f-b4f9-e944e76d768c', '6cc4c486-6f2f-4a68-86de-b21173703f07', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 397.67, 341, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 강원특별자치도 센터 -> 경상남도 센터
       ('c9c1c04e-e3ee-4025-a730-208692a11382', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '6f39d519-4947-4a95-a454-58c25728f388', 130.21, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 서울특별시 센터
       ('e9ccded2-f2d3-44fe-8264-0964dee1bcc5', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 161.76, 139, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 경기 북부 센터
       ('aac7ceca-3a37-4c16-a434-7ae8e83766e5', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '4944c848-671b-4038-a69e-1788b65e9f71', 81.01, 70, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 경기 남부 센터
       ('301b62cc-a291-4946-b7e6-eb62223c675b', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 284.44, 244, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 부산광역시 센터
       ('7553c9d5-956b-4dd9-a930-59033b39e020', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 169.19, 146, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 대구광역시 센터
       ('68c7058a-98f7-4dfa-9366-92fb2037533b', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '6153ad9f-7a1b-4616-b281-078a83484de5', 149.35, 129, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 인천광역시 센터
       ('665ed637-ba6c-4ea5-a17b-b407debe6da2', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 226.06, 194, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 광주광역시 센터
       ('7c16e40f-9966-4af8-9bc4-7da284cb461d', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '93eb97ce-b201-46e0-b26b-584c39191aa9', 43.01, 37, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 대전광역시 센터
       ('05ee259f-9324-42f8-b541-cdf797e21c08', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 266.24, 229, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 울산광역시 센터
       ('1c87718b-614f-48e2-9ac3-4de056f2149f', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 32.53, 28, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 세종특별자치시 센터
       ('4d2ba701-5d4d-47e2-aaec-ee7cdaf5bef0', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '6cc4c486-6f2f-4a68-86de-b21173703f07', 181.23, 156, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 강원특별자치도 센터
       ('4dcd7409-2981-4f2c-8147-126cf3235b56', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', '6090face-fa49-4edb-a089-1bd667aeaf0f', 92.64, 80, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 충청남도 센터
       ('02c04cb6-b934-4150-b97e-e0083086be46', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 126.07, 109, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 전북특별자치도 센터
       ('360ae02e-d7a6-4f12-ac58-237e6a9fbd21', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 289.34, 249, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 전라남도 센터
       ('afdf123f-e8fc-463c-9658-f1cc99676be9', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 117.93, 102, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 경상북도 센터
       ('f5816726-c403-49b0-9e66-1bb277d534aa', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 246.06, 211, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청북도 센터 -> 경상남도 센터
       ('3d3ed44f-0f80-4e86-ada7-68118ed0e6a8', '6090face-fa49-4edb-a089-1bd667aeaf0f', '6f39d519-4947-4a95-a454-58c25728f388', 129.81, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 서울특별시 센터
       ('552018c9-8540-4364-b598-da747180d8e7', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 143.72, 124, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 경기 북부 센터
       ('d218cc18-9984-476b-8ed6-4ddd432bced7', '6090face-fa49-4edb-a089-1bd667aeaf0f', '4944c848-671b-4038-a69e-1788b65e9f71', 110.27, 95, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 경기 남부 센터
       ('847ed05f-bcf4-40e5-8952-52ed168ff7fa', '6090face-fa49-4edb-a089-1bd667aeaf0f', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 353.48, 303, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 부산광역시 센터
       ('d377be89-8c71-4235-8e8f-be992fa97b6d', '6090face-fa49-4edb-a089-1bd667aeaf0f', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 248.82, 214, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 대구광역시 센터
       ('62506fce-d3ff-439d-8ee6-9b7725d277e1', '6090face-fa49-4edb-a089-1bd667aeaf0f', '6153ad9f-7a1b-4616-b281-078a83484de5', 115.59, 100, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 인천광역시 센터
       ('a72e30c9-b648-4912-ba04-6d13d80beb40', '6090face-fa49-4edb-a089-1bd667aeaf0f', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 217.11, 187, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 광주광역시 센터
       ('b290dcf7-7f9e-4ba2-9bba-a21a4ad936e1', '6090face-fa49-4edb-a089-1bd667aeaf0f', '93eb97ce-b201-46e0-b26b-584c39191aa9', 91.74, 79, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 대전광역시 센터
       ('883af60e-7f7c-47b8-9e6b-774d50314686', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 346.82, 298, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 울산광역시 센터
       ('bf28d0be-f99c-4fcf-a9e7-1bf95e7c038d', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 73.71, 64, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 세종특별자치시 센터
       ('6904f7a8-f628-4bf2-b4c7-fd61bc8c8ed7', '6090face-fa49-4edb-a089-1bd667aeaf0f', '6cc4c486-6f2f-4a68-86de-b21173703f07', 212.18, 182, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 강원특별자치도 센터
       ('8c5c5c34-4b5b-44dc-ac6c-1d636bc3347e', '6090face-fa49-4edb-a089-1bd667aeaf0f', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 92.64, 80, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 충청북도 센터
       ('149eec0b-7cda-48f7-93a8-0088504b95b3', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 130.23, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 전북특별자치도 센터
       ('a730c16d-aab4-401e-938e-ffce47f17523', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 267.41, 230, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 전라남도 센터
       ('b203a72b-7aa5-4fb1-80fb-0e8719139424', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 210.55, 181, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 경상북도 센터
       ('dad1b652-e285-432b-bb3d-5513ac05e893', '6090face-fa49-4edb-a089-1bd667aeaf0f', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 311.05, 267, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 충청남도 센터 -> 경상남도 센터
       ('dd3c2c75-dbb4-46ea-b35f-5db5b9068d71', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '6f39d519-4947-4a95-a454-58c25728f388', 240.92, 207, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 서울특별시 센터
       ('d36c90e5-1fd8-49b1-b64f-40a7a324a983', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 264.52, 227, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 경기 북부 센터
       ('f5232ece-8193-4f62-ba8e-2e79c634ff93', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '4944c848-671b-4038-a69e-1788b65e9f71', 200.26, 172, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 경기 남부 센터
       ('39774772-fcdc-4880-95d1-687a7003bd7b', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 249.19, 214, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 부산광역시 센터
       ('15c77043-8446-49d4-8063-3ba2353f8429', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 174.55, 150, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 대구광역시 센터
       ('584027e7-5652-428e-8482-aa9ae719b8c9', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '6153ad9f-7a1b-4616-b281-078a83484de5', 241.06, 207, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 인천광역시 센터
       ('bb738189-61a8-4a6d-90c7-19dec7bf9c89', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 100.06, 86, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 광주광역시 센터
       ('3a386a10-fc96-4467-9549-70208433dbe4', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '93eb97ce-b201-46e0-b26b-584c39191aa9', 83.23, 72, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 대전광역시 센터
       ('b68750a8-046e-4bb4-b9ef-5827fa28963e', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 262.58, 226, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 울산광역시 센터
       ('56c581bb-91e5-461b-872b-d69aec33bca5', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 97.72, 84, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 세종특별자치시 센터
       ('73a5a487-9f03-4646-90be-5f621c70f21a', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '6cc4c486-6f2f-4a68-86de-b21173703f07', 305.49, 262, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 강원특별자치도 센터
       ('69d88a6c-6e9a-450d-8e5f-837f64d0f8d4', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 126.07, 109, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 충청북도 센터
       ('6915afc4-49c4-4fc2-b953-198198d3bc48', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', '6090face-fa49-4edb-a089-1bd667aeaf0f', 130.23, 112, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 충청남도 센터
       ('b1bf7c2a-9774-40cb-ac5b-23dda770b971', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 163.83, 141, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 전라남도 센터
       ('e38401d1-2bab-41c7-b795-0acb54aee879', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 196.17, 169, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 경상북도 센터
       ('9da04ac0-6cab-450d-b3cc-020514fbf4c0', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 204.45, 176, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전북특별자치도 센터 -> 경상남도 센터
       ('f934b6a0-5bfb-4a8a-86be-bc2029108179', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '6f39d519-4947-4a95-a454-58c25728f388', 393.63, 338, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 서울특별시 센터
       ('45155f78-02a4-4210-8624-d68ee4f5b384', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 411.05, 353, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 경기 북부 센터
       ('ea6e4939-e521-45d2-9bdd-2e3db6fb5aed', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '4944c848-671b-4038-a69e-1788b65e9f71', 359.19, 308, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 경기 남부 센터
       ('a6724915-38c0-4075-88ab-5147b862b896', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 308.74, 265, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 부산광역시 센터
       ('4f98aa1a-b5e4-476e-9514-4447ea0094bc', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 294.44, 253, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 대구광역시 센터
       ('0c35c8b8-18df-4e93-a67b-5193af753157', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '6153ad9f-7a1b-4616-b281-078a83484de5', 382.67, 329, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 인천광역시 센터
       ('ca263fd3-ef8c-42ea-a16c-e90d60ba4331', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 67.70, 59, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 광주광역시 센터
       ('6b62a2e8-2251-49a0-b403-b469ca22a012', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '93eb97ce-b201-46e0-b26b-584c39191aa9', 246.85, 212, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 대전광역시 센터
       ('fcbf889b-189c-41d5-bc92-c3b5c9fd53b0', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 352.49, 303, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 울산광역시 센터
       ('7804dc0a-4ee2-4597-83af-eee2d12308ab', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 259.34, 223, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 세종특별자치시 센터
       ('a029e32b-b23e-4f81-8faf-35744d826f34', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '6cc4c486-6f2f-4a68-86de-b21173703f07', 465.86, 400, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 강원특별자치도 센터
       ('e8996646-36ff-46ac-877b-8fa6a9ecfa2a', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 289.34, 249, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 충청북도 센터
       ('ebab4ace-23d2-4529-bbbd-18f01c0f7402', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', '6090face-fa49-4edb-a089-1bd667aeaf0f', 267.41, 230, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 충청남도 센터
       ('0f8bc545-2d07-4437-92bb-dbcffcbdcdf6', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 163.83, 141, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 전북특별자치도 센터
       ('9955ec0e-8d61-4779-8424-1c6be538a090', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 349.44, 300, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 경상북도 센터
       ('b4fae873-f9ff-4ed8-8b0e-c46d338a4d33', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 270.72, 233, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 전라남도 센터 -> 경상남도 센터
       ('0b653fd5-e449-497f-a671-eacf947b083d', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '6f39d519-4947-4a95-a454-58c25728f388', 206.91, 178, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 서울특별시 센터
       ('8995ad8c-59f1-4893-8ee9-aeb931de7a7b', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 243.03, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 경기 북부 센터
       ('fbdc7378-492c-4ea7-a600-5c469d4ca945', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '4944c848-671b-4038-a69e-1788b65e9f71', 157.66, 136, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 경기 남부 센터
       ('f3ce4923-a3a0-40f4-a0b1-1306c4fd12e7', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 219.40, 189, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 부산광역시 센터
       ('74e90ee0-bba0-4df5-b017-7a2fd3bf21ec', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 101.65, 88, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 대구광역시 센터
       ('6180673a-44a9-46a9-9bbc-2ad575f526fe', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '6153ad9f-7a1b-4616-b281-078a83484de5', 243.65, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 인천광역시 센터
       ('ccabd8d4-c9a4-4405-8e30-b6be18db0e05', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 281.74, 242, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 광주광역시 센터
       ('78d6c5e3-47e5-4d96-8812-a7ece9ffcc99', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '93eb97ce-b201-46e0-b26b-584c39191aa9', 134.22, 116, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 대전광역시 센터
       ('c341a8fc-fd75-41a4-a43c-69b57f44e9d9', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 178.21, 153, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 울산광역시 센터
       ('df22984a-dac4-4ea6-9435-31160900be69', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 141.94, 122, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 세종특별자치시 센터
       ('b1221f27-8862-4f55-b489-3d93d94a5926', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '6cc4c486-6f2f-4a68-86de-b21173703f07', 208.57, 179, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 강원특별자치도 센터
       ('9fec401e-cf38-4e27-9fdf-8393f7645e18', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 117.93, 102, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 충청북도 센터
       ('e00b8117-3589-461d-af9e-ebaaa4125103', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', '6090face-fa49-4edb-a089-1bd667aeaf0f', 210.55, 181, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 충청남도 센터
       ('a8a81882-da67-47fb-b25d-7e5345f5299d', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 196.17, 169, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 전북특별자치도 센터
       ('cad62950-abec-4d86-aeac-758f1912c502', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 349.44, 300, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 전라남도 센터
       ('ad444366-17f1-422e-b916-45db1cb12174', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 194.58, 167, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상북도 센터 -> 경상남도 센터
       ('08a94f02-f39c-4914-a32a-4b5d63b7d5c8', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '6f39d519-4947-4a95-a454-58c25728f388', 372.88, 320, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 서울특별시 센터
       ('0fc97a8f-6042-4ef7-8989-e30bdc6eeaa7', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'cfa35654-5a89-49e3-a6af-1d538093a49a', 406.56, 349, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 경기 북부 센터
       ('99689a77-e450-4fd0-8cba-346f630e0f81', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '4944c848-671b-4038-a69e-1788b65e9f71', 320.93, 276, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 경기 남부 센터
       ('2dffd0ac-3a27-4f7a-8576-f772507a566e', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '47fd3fad-0fa3-4ea8-8741-b93c3b9c4d35', 44.78, 39, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 부산광역시 센터
       ('5cd27e44-8fa0-4ba3-8d47-c10c16666a3d', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '6ad7c6db-61b2-4bcb-bcbb-ff23b7b97c12', 92.93, 80, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 대구광역시 센터
       ('578ed357-f472-4c40-a170-db6dcd611929', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '6153ad9f-7a1b-4616-b281-078a83484de5', 395.35, 339, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 인천광역시 센터
       ('ad502703-d75c-4cab-b27f-98225967fcec', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '92db1fc2-984c-4d06-8ad9-6d28169c0404', 217.68, 187, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 광주광역시 센터
       ('7d16ae89-b2f1-42fa-9261-c1b54b5ed6e9', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '93eb97ce-b201-46e0-b26b-584c39191aa9', 222.15, 191, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 대전광역시 센터
       ('0cb4d553-cf7b-49c2-89d6-dfd999fcd373', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'f2987999-13fa-4022-965f-69c8fcb2e7ec', 84.86, 73, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 울산광역시 센터
       ('1a435817-9a8d-4482-9ff8-4d2769949e6f', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'c1eb0ea5-9905-4c19-9ebe-c3c4eba916b1', 243.43, 209, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 세종특별자치시 센터
       ('ffd63455-2e84-44af-a99d-d0249e3e5969', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '6cc4c486-6f2f-4a68-86de-b21173703f07', 397.67, 341, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 강원특별자치도 센터
       ('67c2e429-2bcf-43a8-8f6b-2f9179a6a1fe', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '715c4f51-3f18-4ff4-939e-a1d1cbd1b2a6', 246.06, 211, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 충청북도 센터
       ('f0939c72-6561-4e9f-9b8b-c15540decf19', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', '6090face-fa49-4edb-a089-1bd667aeaf0f', 311.05, 267, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 충청남도 센터
       ('179e1b7a-e08a-4870-b007-d8060f9435d4', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'fd28ad70-bcbf-4cad-b67b-6112f61cdf41', 204.45, 176, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 전북특별자치도 센터
       ('49037c55-aa40-489a-9496-aa8d68ec4f27', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'ea06e966-8dd0-435b-a3d3-46d87e3f2f44', 270.72, 233, NOW(), 'SYSTEM', NOW(), 'SYSTEM'),  -- 경상남도 센터 -> 전라남도 센터
       ('96d9290d-fcb4-4e81-864f-550266df5908', 'b686bb34-1d41-4ecb-8d32-0c2d5df16db2', 'a28c5bd9-abf6-4613-8e3d-79800f6623d5', 194.58, 167, NOW(), 'SYSTEM', NOW(), 'SYSTEM');  -- 경상남도 센터 -> 경상북도 센터
