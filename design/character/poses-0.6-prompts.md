# 助手姿势素材提示词

生成方式：内置 image_gen。以既有 chibi.png 为身份参考；最终素材保存在 app/src/main/assets/character/companion-poses-v1.png。

## 姿势生成

Use case: identity-preserve
Asset type: transparent mobile app chibi companion animation pose atlas, 1536 by 1024 landscape
Input image: identity reference, the existing silver-haired chibi boy. Preserve the same silver lavender simplified parted hair, short large gray-black eyes, rounded chin, small black earrings, simple black jacket with subtle cyan/lavender edge light, black pants, and restrained closed-mouth smile.
Primary request: Create one production-ready TRANSPARENT PNG sprite atlas. Exactly 3 columns and 2 rows of equal 512x512 cells. Same character scale and same head position within each cell. Every cell depicts a STANDING character from head to thighs, not sitting, not cross-legged. The character casually leans toward the LEFT as if resting his right forearm over an invisible dialogue panel whose top is around waist level. No visible furniture/panel, no text, no background. Keep whole head and arms inside each cell, clear gaps between figures.
Top row: (1) calm leaning pose, eyes open, right forearm resting toward left at waist height, left hand relaxed near chest; (2) EXACT SAME leaning pose with both eyes gently closed for blink, same silhouette; (3) SAME leaning pose with encouraging small closed-mouth smile and slightly lifted eyebrows, same silhouette.
Bottom row: (4) standing friendly greeting, right forearm still resting left, left hand raised near shoulder palm out; (5) same greeting with left hand tilted slightly outward and fingers moved subtly for wave animation, same body and head alignment; (6) thoughtful expression, standing leaning with hand lightly near chin.
Style: polished clean anime chibi, simplify internal hair lines, detailed clear fingers, tasteful delicate outlines, understated cyan rim light. Keep original identity. Genuine transparent alpha around all six sprites. No labels, no decorative symbols.

## 透明背景处理

Use case: background-extraction. Edit this exact six-pose character atlas. Remove ALL blue/pink/dark gradient background between and around the characters, output genuine transparent alpha. Preserve the exact existing 1536x1024 canvas, exact 3 columns and 2 rows grid, same pixel positions, same character artwork and scale, all hair edges and fingers. This is a sprite sheet for compositing in a mobile application. No new background, no checkerboard, no color glow outside silhouette. Preserve only characters with clean anti-aliased transparency. Do not redraw faces or poses.
