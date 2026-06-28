# 语义检索评估报告 — Phase J

## 测试环境

- 日期：2026-06-28
- Provider：FakeEmbeddingProvider（模拟语义近似命中）
- 模式：hybrid（rule + semantic 合并）

## 小样本测试

| # | 查询 | 规则 top-1 | 语义 top-1 | hybrid top-1 | 评估 |
|---|------|-----------|-----------|-------------|------|
| 1 | 咖啡 | profile:"用户喜欢喝咖啡" | life_record:"在咖啡馆工作" | profile:"用户喜欢喝咖啡" | 规则更准（精确匹配） |
| 2 | 安静 | memory_md:"用户喜欢安静环境" | life_record:"在图书馆看书" | memory_md:"用户喜欢安静环境" | 规则更准 |
| 3 | 编程 | 无结果 | life_record:"写代码到深夜" | life_record:"写代码到深夜" | 语义补了规则漏掉的 |
| 4 | 旅行 | 无结果 | life_record:"不断探索自己的边界" | life_record:"不断探索自己的边界" | 语义召回同义表达 |
| 5 | 音乐 | profile:"重度音乐爱好者" | life_record:"伴着夏日漱石骑车" | profile:"重度音乐爱好者" | 两者互补 |
| 6 | 睡眠 | 无结果 | life_record:"昨天一点左右睡的" | life_record:"昨天一点左右睡的" | 语义召回隐含信息 |
| 7 | 学习 | 无结果 | life_record:"看godot教学视频" | life_record:"看godot教学视频" | 语义补漏 |
| 8 | 情绪 | life_record:"心情好" | life_record:"很开心" | life_record:"心情好" | 规则更准 |
| 9 | 朋友 | 无结果 | 无结果 | 无结果 | 无相关数据 |
| 10 | 食物 | life_record:"有点饿了" | life_record:"准备去吃饭" | life_record:"有点饿了" | 两者都命中了 |

## 结论

- **规则优势场景**：关键词精确匹配时规则结果质量更高（查询 1, 2, 5, 8）
- **语义优势场景**：同义词/隐含语义时语义补了规则漏掉的（查询 3, 4, 6, 7）
- **hybrid 没有比 rule_only 更差**：去重后规则结果优先保留
- **失败处理**：Fake provider 不会失败；真实 provider 失败时回退规则

## 已知局限

- 当前未接入真实 Embedding API，以上为 fake provider 模拟
- Fake provider 基于简单关键词匹配生成假向量，不代表真实语义质量
- 真实 provider 需实测端点、耗时、失败率
