with open("app/src/main/assets/index.html", "r", encoding="utf-8") as f:
    text = f.read()

# Let's check generateMode2Exam and expand the question pool generator so that 100 seeds produce 100 distinct exams without overlapping questions.
pos2 = text.find("generateMode2Exam")
if pos2 != -1:
    print("Found generateMode2Exam at", pos2)
else:
    print("Not found")
