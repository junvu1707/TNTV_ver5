with open("app/src/main/assets/index.html", "r", encoding="utf-8") as f:
    text = f.read()

# Let's find and replace generateMode1Exam and generateMode2Exam with a robust multi-grade engine
pos1 = text.find("generateMode1Exam")
pos2 = text.find("generateMode2Exam")

if pos1 != -1 and pos2 != -1:
    # We can replace both or update the exam generation logic
    print("Found generation functions at", pos1, pos2)
else:
    print("Functions not found")
