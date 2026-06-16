# BUGs

- [ ] [1.Blocks 列表筛选](#1blocks-列表筛选)

- [ ] [2.Blocks 列表筛选](#2blocks-icon模式筛选功能不生效)

## 1.Blocks 列表筛选

[blocks](app/blocks/page.tsx#L239-L245)

列表选择时，如果选择左上角的全选，将激活select的type改变

暂时先设置了不允许全选

> ps：后期修改时得修改的逻辑：已选择的物品数量、icon显示

## 2.Blocks icon模式筛选功能不生效

[blocks](app/blocks/page.tsx#L180-L186)

因为表格已经卸载，ref为空

## 3.重渲染

因使用列表顺序做为key，每一次变动都会导致重渲染

```tsx
{
  something.map((item, i) => <div key={i}>{item.name}</div>);
}
```
