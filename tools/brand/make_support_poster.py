from PIL import Image, ImageDraw, ImageFont
W,H=1280,720
bg=Image.new("RGB",(W,H))
px=bg.load()
for y in range(H):
    t=y/H
    c=(int(22-12*t),int(20-8*t),int(52-30*t))
    for x in range(W): px[x,y]=c
d=ImageDraw.Draw(bg,"RGBA")
# soft glow
for r,a in ((420,22),(300,28),(180,34)):
    d.ellipse((W-r-60,-r//2,W+r-60,r*3//2-r//2),fill=(91,62,214,a))
B="/usr/share/fonts/opentype/inter/Inter-Bold.otf"; R="/usr/share/fonts/opentype/inter/Inter-Regular.otf"; M="/usr/share/fonts/opentype/inter/Inter-Medium.otf"
f=lambda p,s: ImageFont.truetype(p,s)
logo=Image.open("/home/claude/repo/work/brand/logo-512-square.png").convert("RGBA").resize((150,150))
m=Image.new("L",(150,150),0); ImageDraw.Draw(m).rounded_rectangle((0,0,149,149),radius=36,fill=255)
bg.paste(logo,(80,70),m)
d.text((255,92),"YAiPlayer",font=f(B,54),fill="white")
d.text((257,160),"Центр поддержки",font=f(R,28),fill=(170,174,204))
d.text((80,265),"Нашли ошибку?",font=f(B,84),fill="white")
d.text((80,365),"Или хотите новую функцию?",font=f(B,52),fill=(34,211,238))
items=[("Баг или сбой",(239,68,68)),("Нет функции",(250,204,21)),("Идея для плеера",(74,222,128))]
x=80
for text,col in items:
    ft=f(M,28); w=int(d.textlength(text,font=ft))+84
    d.rounded_rectangle((x,470,x+w,534),radius=32,fill=(255,255,255,26),outline=(255,255,255,60),width=2)
    d.ellipse((x+22,490,x+48,516),fill=col)
    d.text((x+64,484),text,font=ft,fill="white")
    x+=w+22
d.text((80,572),"Напишите нам — разберёмся и исправим",font=f(M,34),fill=(215,218,238))
# buttons
def btn(x,y,w,label,fill):
    d.rounded_rectangle((x,y,x+w,y+66),radius=33,fill=fill)
    ft=f(B,28); tw=d.textlength(label,font=ft)
    d.text((x+(w-tw)/2,y+17),label,font=ft,fill="white")
btn(80,630,420,"Поддержка: t.me/YAiPlayer",(91,62,214))
btn(520,630,420,"Чат: t.me/YAiPlayer_Chat",(14,165,200))
bg.save("support-poster-1280x720.png",optimize=True)
