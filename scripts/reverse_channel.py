import cv2

prefix = "src/main/resources/assets/mekanismheated/textures"
path = f"{prefix}/block/models/cooler_led.png"
image = cv2.imread(path)
swapped = image[:, :, ::-1]
cv2.imwrite(path, swapped)
