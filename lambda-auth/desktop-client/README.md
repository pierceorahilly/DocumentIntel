# BankBuddy Desktop Client

🖥️ **Java Swing desktop app** - Run immediately and see the GUI!

## ✨ Features

- ✅ Login/Signup with email confirmation
- ✅ PDF file picker
- ✅ Upload to AWS Lambda
- ✅ Display financial advice from Claude AI
- ✅ View extracted transactions
- ✅ **DEMO MODE** - Works without AWS backend!

## 🚀 How to Run (3 Ways)

### Option 1: IntelliJ IDEA (Easiest!)

1. **Open Project**:
   - File → Open → Select `D:\simple-bank-ai_patch\lambda-auth\desktop-client`

2. **Run**:
   - Navigate to `src/main/java/com/example/desktop/BankBuddyApp.java`
   - Right-click → **Run 'BankBuddyApp.main()'**

3. **GUI appears!** 🎉

### Option 2: Command Line (Maven)

```bash
cd D:\simple-bank-ai_patch\lambda-auth\desktop-client
mvn clean compile exec:java
```

GUI launches immediately!

### Option 3: Build JAR and Double-Click

```bash
cd D:\simple-bank-ai_patch\lambda-auth\desktop-client
mvn clean package
java -jar target/bankbuddy-desktop-1.0.0.jar
```

Or just **double-click** the JAR file!

---

## 🎮 Try It Now (Demo Mode)

The app starts in **DEMO MODE** by default - you can use it WITHOUT deploying AWS!

1. **Run the app** (any method above)
2. **Click "Sign up"** (enter any email/password)
3. **Login** (use same email/password)
4. **Click "Select PDF File"** (choose any PDF)
5. **Click "Upload & Analyze"**
6. **See sample financial advice!** 💰

Demo mode shows realistic fake data so you can test the interface.

---

## 🔧 Connect to AWS (When Ready)

When you deploy your AWS Lambda backend:

1. **Edit**: `src/main/java/com/example/desktop/utils/Config.java`

2. **Change**:
   ```java
   public static final String API_BASE_URL =
       "https://YOUR_API_ID.execute-api.eu-west-1.amazonaws.com/prod";

   public static final boolean DEMO_MODE = false;  // Disable demo mode
   ```

3. **Rebuild and run**

Now it connects to your real AWS backend!

---

## 📸 Screenshots

### Login Screen
```
┌─────────────────────────────────┐
│        BankBuddy               │
│  Your AI Financial Companion   │
│                                 │
│  Email:    [____________]       │
│  Password: [____________]       │
│                                 │
│         [ Login ]               │
│                                 │
│  Don't have an account? Sign up │
└─────────────────────────────────┘
```

### Upload Screen
```
┌─────────────────────────────────┐
│  Welcome, user@example.com!  [Logout]
│                                 │
│  Upload your bank statement PDF │
│                                 │
│     [ Select PDF File ]         │
│  Selected: statement.pdf        │
│     [ Upload & Analyze ]        │
│                                 │
│  ┌─────────────────────────┐   │
│  │ 💡 Financial Advice     │   │
│  │ (AI advice here...)     │   │
│  ├─────────────────────────┤   │
│  │ 📊 Transactions         │   │
│  │ Date  Desc  Amount      │   │
│  │ ...                     │   │
│  └─────────────────────────┘   │
└─────────────────────────────────┘
```

---

## 🎨 Features Breakdown

| Feature | Status | Description |
|---------|--------|-------------|
| **Login** | ✅ Ready | Email + password authentication |
| **Signup** | ✅ Ready | Create new account |
| **Email Confirm** | ✅ Ready | Enter 6-digit code |
| **Demo Mode** | ✅ Ready | Works without AWS! |
| **PDF Upload** | ✅ Ready | File picker for PDFs |
| **AI Advice** | ✅ Ready | Claude savings recommendations |
| **Transactions** | ✅ Ready | Formatted table view |
| **Token Storage** | ✅ Ready | Secure JWT storage |
| **Modern UI** | ✅ Ready | FlatLaf look and feel |

---

## 📁 Project Structure

```
desktop-client/
├── pom.xml                           # Maven dependencies
├── src/main/java/com/example/desktop/
│   ├── BankBuddyApp.java            # ⭐ RUN THIS FILE!
│   ├── api/
│   │   └── ApiClient.java           # AWS API integration
│   ├── ui/
│   │   ├── LoginPanel.java          # Login/signup screen
│   │   └── MainPanel.java           # Upload & results screen
│   └── utils/
│       ├── Config.java              # API URL config
│       └── TokenManager.java        # JWT token storage
└── README.md                        # This file
```

---

## 🔐 Security

- ✅ JWT tokens stored securely (Java Preferences API)
- ✅ HTTPS only for API calls
- ✅ No passwords stored
- ✅ Demo mode doesn't send data anywhere

---

## 💡 Tips

### Change Window Size

Edit `BankBuddyApp.java` line 28:
```java
setSize(900, 700);  // Change to your preferred size
```

### Change Theme

The app uses **FlatLaf Light** theme. To change to dark:

Edit `BankBuddyApp.java` line 64:
```java
// Light theme (default)
FlatLightLaf.setup();

// OR Dark theme
FlatDarkLaf.setup();
```

### Test with Sample PDF

Any PDF works in demo mode! For real testing, use a bank statement PDF (70KB typical size).

---

## 🐛 Troubleshooting

### "Could not find or load main class"
- Make sure you're in the `desktop-client` directory
- Run `mvn clean compile` first

### "java: error: release version 17 not supported"
- Install Java 17 (or higher)
- Or change `pom.xml` lines 12-13 to your Java version

### GUI doesn't appear
- Check console for errors
- Try: `mvn clean compile exec:java -e` (shows detailed errors)

### "Connection refused" in demo mode
- This is normal! Demo mode doesn't connect to server
- Just shows sample data

### "Connection refused" in real mode
- Check API_BASE_URL in Config.java
- Ensure AWS Lambda is deployed
- Test API with Postman first

---

## 🎯 What's Next?

1. ✅ Run the app now (demo mode)
2. ✅ Play with the interface
3. 🔲 Deploy AWS Lambda backend
4. 🔲 Update Config.java with real API URL
5. 🔲 Disable demo mode
6. 🔲 Test real upload!

---

## 📝 Quick Start Checklist

- [ ] Java 17 installed
- [ ] Maven installed (or use IntelliJ)
- [ ] Open `desktop-client` folder
- [ ] Run `BankBuddyApp.java`
- [ ] See the GUI!
- [ ] Try demo mode
- [ ] (Later) Connect to AWS

---

**Ready? Just run `BankBuddyApp.java` and the GUI appears!** 🚀

The app is in **DEMO MODE** by default, so you can see it working immediately without any AWS setup!
