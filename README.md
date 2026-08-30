# Reps

> **One more rep.** 把老师下发的题库 CSV，一键变成手机上可反复背诵的离线刷题应用。

原生 Android · Kotlin · Jetpack Compose (Material 3) · Room/SQLite · **完全离线 · 无广告 · MIT 开源**

## 文档

| 文档 | 内容 |
|---|---|
| [docs/PRODUCT.md](docs/PRODUCT.md) | 产品定位、竞品调研、功能需求与验收标准、MVP 路线 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 技术选型、架构、Room 数据模型、CSV 导入管线、会话保存/恢复设计、测试与发布清单 |

## CSV 题库格式

```csv
id,content,type,option_a,option_b,option_c,option_d,option_e,correct_answer,explanation,category,chapter
1,马克思主义产生的经济根源是,single,工业革命,资本主义生产方式,封建社会,奴隶社会,,B,解析内容,马原,第一章
```

- `type`：`single`（单选）/ `judge`（判断），多选与填空规划中
- 编码支持 UTF-8（含 BOM）与 GBK/GB18030 自动识别
- MVP 通过系统文件选择器导入，导入前可预览校验

## 开发进度

- [x] Phase 0 — 调研、产品/开发文档
- [ ] Phase 1 — 骨架（数据库、导航、空状态）
- [ ] Phase 2 — 题库导入与刷题闭环
- [ ] Phase 3 — 会话恢复与错题闭环
- [ ] Phase 4 — 打磨与发布

## License

MIT（发布时附 LICENSE 文件）
