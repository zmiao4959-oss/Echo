# 主题系统

## 五种主题

定义在 `res/values/themes.xml`，通过 `ThemeManager` 运行时切换。

| Key | 显示名 | 主色 | Surface | SurfaceVariant | Background |
|-----|--------|------|---------|----------------|-------------|
| `warm_tea` | 暖茶 | `#2F7D7A` | `#FFFFFF` | `#F3EFE7` | `#FAF7F0` |
| `forest` | 森林绿 | `#4A8C5C` | `#FFFFFF` | `#EBF2E8` | `#F6F9F0` |
| `ocean` | 深海蓝 | `#3A6999` | `#FFFFFF` | `#E8EDF5` | `#F2F5FA` |
| `twilight` | 暮色紫 | `#7B5EA7` | `#FFFFFF` | `#EEEAF2` | `#F6F4F8` |
| `dark` | 暗夜 | `#BB86FC` | `#1E1E1E` | `#2C2C2C` | `#121212` |

## 属性定义

17 个自定义 attr（`res/values/attrs.xml`），格式 `reference|color`：

- 卡片相关：`echoSurface`、`echoSurfaceVariant`
- 文字相关：`echoTextPrimary`、`echoTextSecondary`、`echoHint`
- 品牌相关：`echoPrimary`、`echoPrimaryDark`、`echoOnPrimary`、`echoAccent`
- 气泡相关：`echoBubbleUser`、`echoBubbleAssistant`、`echoToolInfoBg`
- 辅助：`echoBackground`、`echoBorder`、`echoDestructive`

## 切换流程

1. 用户选主题 → `config.themeKey = newKey`
2. `ThemeManager.pendingChange = true`
3. `ProfileActivity.finish()` + `restart`
4. `MainActivity.onResume()` 检测 `pendingChange` → `recreate()`
5. 所有 Fragment/卡片通过 `?attr/echo*` 自动更新颜色

## 字体

`FontManager` 管理 4 种字体（default / light / serif / mono），通过 `AppConfig.fontKey` 切换，在 `ThemedActivity` 中应用。
