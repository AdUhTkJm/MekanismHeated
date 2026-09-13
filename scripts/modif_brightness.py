import cv2

prefix = "src/main/resources/assets/mekanismheated/textures/block"
img = cv2.imread(f"{prefix}/r.png", cv2.IMREAD_UNCHANGED)
bright_img = cv2.convertScaleAbs(img, alpha=1.0, beta=50)
cv2.imwrite("output.png", bright_img)
