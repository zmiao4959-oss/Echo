# 主题系统

## 八套主题

主题由 `ThemeManager` 统一描述，并在 `res/values/themes.xml` 中提供完整色彩令牌。每套主题都拥有独立名称、说明、明暗模式、预览色和背景表达，不是同一模板的换色版本。

| Key | 名称 | 类型 | 核心表达 |
|---|---|---|---|
| `paper_atelier` | 纸本工坊 | 静态 | 暖象牙手工纸、植物压纹、鼠尾草与陶土色 |
| `abyssal_archive` | 深海档案 | 静态 | 深海蓝黑、等深线、冷青荧光 |
| `film_dusk` | 胶片暮色 | 静态 | 烟熏梅紫、旧胶片颗粒、橘色漏光 |
| `cedar_study` | 雪松书房 | 静态 | 雪松木色、百叶光影、压叶与黄铜 |
| `tidal_light` | 流光潮汐 | 动态 | 青蓝与淡紫渐变、缓慢流动的潮汐曲线 |
| `orbital_night` | 星轨夜航 | 动态 | 深夜星空、轨道线、缓慢运行的微光天体 |
| `breathing_grove` | 呼吸森林 | 动态 | 苔绿雾光、有机色块、呼吸般浮动的叶影 |
| `ink_rain` | 墨雨 | 动态 | 灰白宣纸、斜雨、墨晕与水面涟漪 |

四套静态主题的竖屏原画和列表缩略图位于 `res/drawable-nodpi/`。四套动态主题由 `EchoEnvironmentView` 实时绘制，动画会根据当前主题自动启停，并限制刷新频率以控制持续渲染开销。

## 切换与背景

1. 个人页的主题选择器展示原画、说明、静态/动态标记与专属配色。
2. 选择主题后写入 `config.themeKey`，同时将 `backgroundKey` 恢复为 `theme`。
3. Activity 重建后，所有 `?attr/echo*` 色彩令牌与主题环境一起更新。
4. 设置页仍保留手动底色选择；选择手动底色时，它会覆盖主题环境。选择“跟随当前主题”即可恢复主题专属背景。

旧主题键会自动映射，已有安装不会因为升级丢失主题偏好：

| 旧 Key | 新 Key |
|---|---|
| `warm_tea` | `paper_atelier` |
| `forest` | `breathing_grove` |
| `ocean` | `abyssal_archive` |
| `twilight` | `film_dusk` |
| `dark` | `orbital_night` |

## 扩展方式

新增主题时，在 `ThemeManager.ThemeSpec` 中登记元数据，在 `themes.xml` 中补齐色彩令牌；静态主题提供原画资源，动态主题则新增一个 `Motion` 类型并在 `EchoEnvironmentView` 中实现绘制。单元测试会校验主题总数、静态素材完整性，以及动态效果的一致性和唯一性。
