/* The starter set of policies (English + Hindi). An admin adds them from the Policies tab, edits them to match the company, and only then switches them on.
   They are a DRAFT written from what the app really does; they are not legal advice. The company's owner (or its lawyer) should read them before they are published.
   Text format and placeholders: see policy-lib.js.  [Square-bracket prompts] mark what only the company can decide. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.AmkPolicyDefaults = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';
  var P = [];

  /* ────────────────────────── Privacy Policy ────────────────────────── */
  P.push({
    slug: 'privacy', kind: 'legal', sort: 1, pub: 0, must: 0, recommend: { pub: 1, must: 1 },
    title: 'Privacy Policy', titleHi: 'गोपनीयता नीति',
    body: `## About this policy
{{company}} ("the company", "we") gives its staff this app to mark attendance, apply for leave, record meetings and customer visits, and work on tasks. This policy explains what the app collects, why, who can see it, how long it is kept and what choices you have. It follows the Digital Personal Data Protection Act, 2023 and applies to every person who signs in to the app.

## What the app collects
- **Your profile:** name, employee ID, phone, e-mail, department, designation, date of birth, address, emergency contact and the picture you add.
- **Attendance:** punch-in and punch-out times, a selfie at each, and the phone's location at those moments.
- **Location during office hours:** while you are punched in, the app records your phone's location at regular intervals. Nothing is recorded before you punch in, after you punch out or after the closing time (7:30 PM unless the company sets another time). Please read the Location & Tracking Notice for the details.
- **Work records:** meetings and customer visits (customer name, phone, notes, photos), site work, overtime, leave requests, medical documents, tasks and chat messages with their attachments.
- **Phone details:** the phone's battery level and whether it is charging (sent with location checks), a code that lets us send you notifications, and the time the app was last opened.
- **Face check:** when you take a selfie, your phone checks that a face is visible. This check runs on your phone. Only the photo is stored, not a face template.

## Why we use it
- To record attendance and working hours, and to work out salary, leave and overtime.
- To see that staff are at their work place during office hours, and to help in an emergency (the SOS button).
- To run customer visits, tasks and reports for your supervisor and the company.
- To send you reminders and notices about your work.

We do not sell your information and we do not use it for advertising.

## Who can see it
- You can see your own records.
- Your supervisor, your department admin and the company's admin or owner can see the attendance, location checks and work records of the staff in their charge. Medical documents are seen by admins only.
- Other staff see only the team leave calendar (names and days, never the reason) and the tasks and chats you share with them.

## Where it is kept and for how long
- Your information is stored with Google Firebase and Google Cloud. The main database and the servers that run the app are in India (Mumbai).
- Attendance, location and work records are kept for as long as the company needs them for attendance, salary, legal and audit purposes, and are deleted after that.
- Chat attachments stay in the cloud for 5 days. Your phone keeps its own copy, so please download anything you need to keep.
- Medical documents are kept only for a short time after they have been reviewed and are then deleted by the app.

## Other services the app uses
- **Google Firebase / Google Cloud:** storage, sign-in and notifications.
- **OpenStreetMap:** to show a place name for a location, the app sends the latitude and longitude of that location to OpenStreetMap's address service. Admin maps use OpenStreetMap tiles.
- **Face detection:** the small model that finds a face in your selfie is downloaded to your phone from public content networks.

## Your choices and rights
You may ask the company to show you the information it holds about you, to correct it, to delete it where the law allows, and to name someone who may act for you. Write to the grievance officer below. If you do not accept the Location & Tracking Notice you can sign out; the company will then tell you how your attendance is recorded instead. Keep your password private and sign out of shared phones.

## Children
The app is only for employees who are 18 years or older.

## Changes
When this policy changes, the app asks you to read and accept the new version. The version and date are shown at the top of the page.

## Grievance officer and contact
**{{officer}}**, {{company}}
{{address}}
E-mail: {{email}} · Phone: {{phone}}`,
    bodyHi: `## इस नीति के बारे में
{{company}} ("कंपनी", "हम") अपने कर्मचारियों को यह ऐप हाज़िरी लगाने, छुट्टी माँगने, मीटिंग और ग्राहक विज़िट दर्ज करने तथा काम (टास्क) करने के लिए देती है। यह नीति बताती है कि ऐप क्या जानकारी लेता है, क्यों लेता है, उसे कौन देख सकता है, कितने समय रखी जाती है और आपके पास क्या अधिकार हैं। यह डिजिटल व्यक्तिगत डेटा संरक्षण अधिनियम, 2023 के अनुसार है और ऐप में साइन-इन करने वाले हर व्यक्ति पर लागू होती है।

## ऐप क्या जानकारी लेता है
- **आपकी प्रोफ़ाइल:** नाम, कर्मचारी आईडी, फ़ोन, ई-मेल, विभाग, पद, जन्म तिथि, पता, आपातकालीन संपर्क और आपकी लगाई हुई फ़ोटो।
- **हाज़िरी:** पंच-इन और पंच-आउट का समय, दोनों पर सेल्फ़ी और उसी समय फ़ोन की लोकेशन।
- **ऑफ़िस समय में लोकेशन:** जब तक आप पंच-इन हैं, ऐप समय-समय पर फ़ोन की लोकेशन दर्ज करता है। पंच-इन से पहले, पंच-आउट के बाद या बंद होने के समय (कंपनी दूसरा समय न रखे तो शाम 7:30 PM) के बाद कुछ दर्ज नहीं होता। विवरण के लिए "लोकेशन और ट्रैकिंग सूचना" पढ़ें।
- **काम का रिकॉर्ड:** मीटिंग और ग्राहक विज़िट (ग्राहक का नाम, फ़ोन, नोट, फ़ोटो), साइट वर्क, ओवरटाइम, छुट्टी के आवेदन, मेडिकल दस्तावेज़, टास्क और चैट संदेश अपने अटैचमेंट के साथ।
- **फ़ोन की जानकारी:** फ़ोन की बैटरी कितनी है और चार्ज हो रहा है या नहीं (लोकेशन चेक के साथ), नोटिफ़िकेशन भेजने का कोड, और ऐप आख़िरी बार कब खोला गया।
- **चेहरे की जाँच:** सेल्फ़ी लेते समय आपका फ़ोन देखता है कि चेहरा दिख रहा है या नहीं। यह जाँच आपके फ़ोन पर ही होती है। केवल फ़ोटो रखी जाती है, चेहरे का कोई टेम्पलेट नहीं।

## हम इसका उपयोग क्यों करते हैं
- हाज़िरी और काम के घंटे दर्ज करने तथा वेतन, छुट्टी और ओवरटाइम निकालने के लिए।
- यह देखने के लिए कि ऑफ़िस समय में कर्मचारी अपने कार्यस्थल पर हैं, और आपात स्थिति (SOS बटन) में मदद के लिए।
- आपके सुपरवाइज़र और कंपनी के लिए ग्राहक विज़िट, टास्क और रिपोर्ट चलाने के लिए।
- आपको काम से जुड़े रिमाइंडर और सूचनाएँ भेजने के लिए।

हम आपकी जानकारी बेचते नहीं हैं और विज्ञापन के लिए उसका उपयोग नहीं करते।

## इसे कौन देख सकता है
- आप अपना रिकॉर्ड देख सकते हैं।
- आपके सुपरवाइज़र, विभाग के एडमिन और कंपनी के एडमिन या मालिक अपने अधीन कर्मचारियों की हाज़िरी, लोकेशन चेक और काम का रिकॉर्ड देख सकते हैं। मेडिकल दस्तावेज़ केवल एडमिन देखते हैं।
- बाकी कर्मचारी सिर्फ़ टीम का छुट्टी कैलेंडर (नाम और दिन, कारण कभी नहीं) तथा वे टास्क और चैट देखते हैं जो आप उनके साथ साझा करें।

## कहाँ और कितने समय रखी जाती है
- आपकी जानकारी Google Firebase और Google Cloud के पास रखी जाती है। मुख्य डेटाबेस और ऐप चलाने वाले सर्वर भारत (मुंबई) में हैं।
- हाज़िरी, लोकेशन और काम का रिकॉर्ड उतने समय तक रखा जाता है जितना कंपनी को हाज़िरी, वेतन, क़ानूनी और ऑडिट के लिए चाहिए, उसके बाद हटा दिया जाता है।
- चैट अटैचमेंट क्लाउड में 5 दिन रहते हैं। आपके फ़ोन में उनकी अपनी कॉपी रहती है, इसलिए जो चाहिए उसे डाउनलोड कर लें।
- मेडिकल दस्तावेज़ जाँच के बाद थोड़े समय के लिए ही रखे जाते हैं, फिर ऐप उन्हें हटा देता है।

## ऐप की अन्य सेवाएँ
- **Google Firebase / Google Cloud:** स्टोरेज, साइन-इन और नोटिफ़िकेशन।
- **OpenStreetMap:** किसी लोकेशन का स्थान-नाम दिखाने के लिए ऐप उस लोकेशन का अक्षांश-देशांतर OpenStreetMap की पता-सेवा को भेजता है। एडमिन के नक़्शे OpenStreetMap से आते हैं।
- **चेहरा पहचानना:** सेल्फ़ी में चेहरा ढूँढने वाला छोटा मॉडल सार्वजनिक कंटेंट नेटवर्क से आपके फ़ोन पर डाउनलोड होता है।

## आपके विकल्प और अधिकार
आप कंपनी से पूछ सकते हैं कि आपके बारे में कौन-सी जानकारी है, उसे ठीक करवा सकते हैं, क़ानून अनुमति दे तो हटवा सकते हैं, और अपनी ओर से किसी को नामित कर सकते हैं। नीचे दिए शिकायत अधिकारी को लिखें। यदि आप लोकेशन और ट्रैकिंग सूचना स्वीकार नहीं करते तो साइन-आउट कर सकते हैं; तब कंपनी बताएगी कि आपकी हाज़िरी कैसे दर्ज होगी। अपना पासवर्ड गुप्त रखें और साझा फ़ोन से साइन-आउट करें।

## बच्चे
यह ऐप केवल 18 वर्ष या उससे अधिक आयु के कर्मचारियों के लिए है।

## बदलाव
जब यह नीति बदलती है तो ऐप आपसे नया संस्करण पढ़ने और स्वीकार करने को कहता है। संस्करण और तारीख़ पन्ने के ऊपर दिखती है।

## शिकायत अधिकारी और संपर्क
**{{officer}}**, {{company}}
{{address}}
ई-मेल: {{email}} · फ़ोन: {{phone}}`
  });

  /* ────────────────────────── Terms of Use ────────────────────────── */
  P.push({
    slug: 'terms', kind: 'legal', sort: 2, pub: 0, must: 0, recommend: { pub: 1, must: 1 },
    title: 'Terms of Use', titleHi: 'उपयोग की शर्तें',
    body: `## Who these terms are for
These terms are between {{company}} ("the company") and each employee who uses the staff attendance app ("you"). By signing in and tapping Accept you agree to them. Questions: {{email}}.

## Your account
- Your sign-in belongs to you alone. Do not share your password, a sign-in code or your phone's unlock with anyone, and sign out of any phone that is not yours.
- Keep your phone number and e-mail in the app correct so that codes and notices reach you.
- Everything done through your sign-in is treated as done by you.

## Using the app honestly
- Punch in and out yourself, at the time you really start and finish work, with your own face in the selfie.
- Do not fake your location, use a mock-location app, take a photo of a photo, or punch for someone else.
- Record meetings, visits, site work, overtime and leave truthfully. Claims for travel or other costs must be genuine.
- Do not try to break into, overload or damage the app, or look at records you are not allowed to see.

## Phone permissions
The app needs the phone's location and camera to work, and notifications to remind you. Keep Location turned on and allow the app to use it while you are punched in. If the phone blocks these, attendance and reminders may not work and the Location & Tracking Notice describes what happens.

## Records and corrections
- Attendance and work records made through the app are the company's official records.
- If something is wrong, use Fix a punch or ask your supervisor. An admin may correct a record; every correction is logged with who made it and when.

## If the rules are broken
Breaking these terms or the company's attendance rules can lead to action under the company's service and disciplinary rules. The company may pause an account while it looks into a matter.

## The app itself
The company works to keep the app available and accurate but cannot promise it will never be down or wrong; the app depends on your phone, its signal and online services. Where the app fails, the company will decide the day's record fairly. The app and its design belong to the company or its provider and may not be copied.

## Changes and law
The company may change these terms; the app will ask you to accept the new version. These terms are governed by the laws of India, and the courts at the place of the company's registered office have jurisdiction.

## Contact
{{company}}, {{address}} · {{email}} · {{phone}}`,
    bodyHi: `## ये शर्तें किसके लिए हैं
ये शर्तें {{company}} ("कंपनी") और स्टाफ़ अटैंडेंस ऐप का उपयोग करने वाले हर कर्मचारी ("आप") के बीच हैं। साइन-इन करके और स्वीकार (Accept) दबाकर आप इन्हें मानते हैं। प्रश्न: {{email}}।

## आपका खाता
- आपका साइन-इन सिर्फ़ आपका है। अपना पासवर्ड, साइन-इन कोड या फ़ोन का अनलॉक किसी को न दें और जो फ़ोन आपका नहीं है उससे साइन-आउट करें।
- ऐप में अपना फ़ोन नंबर और ई-मेल सही रखें ताकि कोड और सूचनाएँ आप तक पहुँचें।
- आपके साइन-इन से जो भी किया जाता है उसे आपका ही किया हुआ माना जाता है।

## ऐप का ईमानदारी से उपयोग
- पंच-इन और पंच-आउट ख़ुद करें, जब सच में काम शुरू और ख़त्म करें, अपनी ही फ़ोटो के साथ।
- नक़ली लोकेशन, मॉक-लोकेशन ऐप, फ़ोटो की फ़ोटो या किसी और के लिए पंच न करें।
- मीटिंग, विज़िट, साइट वर्क, ओवरटाइम और छुट्टी सच-सच दर्ज करें। यात्रा या अन्य ख़र्च के दावे असली होने चाहिए।
- ऐप में सेंध लगाने, उसे ओवरलोड या ख़राब करने, या वे रिकॉर्ड देखने की कोशिश न करें जिनकी आपको अनुमति नहीं है।

## फ़ोन की अनुमतियाँ
ऐप को चलने के लिए फ़ोन की लोकेशन और कैमरा चाहिए, और याद दिलाने के लिए नोटिफ़िकेशन। पंच-इन रहते समय लोकेशन चालू रखें और ऐप को उसकी अनुमति दें। यदि फ़ोन इन्हें रोकता है तो हाज़िरी और रिमाइंडर काम नहीं कर सकते; क्या होगा यह लोकेशन और ट्रैकिंग सूचना में लिखा है।

## रिकॉर्ड और सुधार
- ऐप से बने हाज़िरी और काम के रिकॉर्ड कंपनी के आधिकारिक रिकॉर्ड हैं।
- कुछ ग़लत हो तो "पंच ठीक करें" का उपयोग करें या अपने सुपरवाइज़र से कहें। एडमिन रिकॉर्ड सुधार सकता है; हर सुधार दर्ज होता है कि किसने और कब किया।

## नियम तोड़ने पर
इन शर्तों या कंपनी के हाज़िरी नियमों को तोड़ने पर कंपनी के सेवा और अनुशासन नियमों के अनुसार कार्रवाई हो सकती है। जाँच के दौरान कंपनी खाता रोक सकती है।

## ऐप के बारे में
कंपनी ऐप को चालू और सही रखने की कोशिश करती है, पर यह वादा नहीं कर सकती कि वह कभी बंद या ग़लत नहीं होगा; ऐप आपके फ़ोन, सिग्नल और ऑनलाइन सेवाओं पर निर्भर है। जहाँ ऐप विफल हो, वहाँ कंपनी उस दिन का रिकॉर्ड निष्पक्ष ढंग से तय करेगी। ऐप और उसका डिज़ाइन कंपनी या उसके प्रदाता का है और उसकी नक़ल नहीं की जा सकती।

## बदलाव और क़ानून
कंपनी ये शर्तें बदल सकती है; ऐप आपसे नया संस्करण स्वीकार करने को कहेगा। ये शर्तें भारत के क़ानूनों के अधीन हैं और कंपनी के पंजीकृत कार्यालय वाले स्थान की अदालतों का अधिकार-क्षेत्र होगा।

## संपर्क
{{company}}, {{address}} · {{email}} · {{phone}}`
  });

  /* ────────────────────────── Location & Tracking Notice ────────────────────────── */
  P.push({
    slug: 'tracking', kind: 'legal', sort: 3, pub: 0, must: 0, recommend: { pub: 1, must: 1 },
    title: 'Location & Tracking Notice', titleHi: 'लोकेशन और ट्रैकिंग सूचना',
    body: `## Why we ask
{{company}} uses your phone's location during office hours to keep attendance fair for everyone and to help you in an emergency. The same rules apply to every employee. This notice says exactly when location is recorded, what is recorded and what happens if it is switched off. Your acceptance is recorded in the app.

## When location is recorded
- Only while you are punched in: from punch-in until punch-out, and never after the closing time of 7:30 PM (unless the company sets another time).
- Nothing is recorded before you punch in, after you punch out, on holidays or on leave.
- The phone checks your location every few minutes while you are punched in. The company sets how often.

## What is recorded
- The place (latitude and longitude), how accurate it is, the time, and the phone's battery level and whether it is charging.
- Nothing else: the app does not record sound, your screen, your messages, your contacts or your photos.

## Work place and time away
- Each office or factory has a boundary on the map. On an Office or Factory day your location is compared with it.
- If several location checks in a row show you outside the boundary, the time you were away is not counted in your hours, and the app tells you. Time on a meeting or customer visit that you started in the app, on Field work, or on urgent personal work you started in the app is kept separately and is not treated as "away".
- If the checks show you moving between places, the app shows you as Travelling and that time is not counted in working hours.

## If your location is off
- Please keep Location turned on and allow the app to use it. If your phone sends no location, the app first sends you a message, by default after 45 minutes.
- If it is still off after 90 minutes, your supervisor and the company's admin are told by e-mail.
- If it is still off after 120 minutes, your day is closed automatically at the last time your location was received, and it is marked Auto. The company can change these times or switch this off.
- If your day was closed by mistake, open the app and tap Resume work. The time in between is kept as personal work.

## Who can see it
Your supervisor, your department admin and the company's admin or owner can see your location checks and your day's route. You can see how many checks were made today and the last place recorded. Other staff cannot see your location.

## How it is used and kept
Location is used for attendance, working hours, safety and reports. It is kept as described in the Privacy Policy, and is not sold or used for advertising.

## Your choice
You can refuse by signing out; the company will then tell you how your attendance will be recorded. The law on personal data also lets you ask the company about your data. Write to {{officer}}, {{email}}, {{phone}}.`,
    bodyHi: `## हम क्यों पूछते हैं
{{company}} ऑफ़िस समय में आपके फ़ोन की लोकेशन इसलिए लेती है कि हाज़िरी सबके लिए निष्पक्ष रहे और आपात स्थिति में आपकी मदद हो सके। यही नियम हर कर्मचारी पर लागू होते हैं। यह सूचना बताती है कि लोकेशन कब दर्ज होती है, क्या दर्ज होता है और बंद करने पर क्या होता है। आपकी सहमति ऐप में दर्ज होती है।

## लोकेशन कब दर्ज होती है
- केवल पंच-इन रहते समय: पंच-इन से पंच-आउट तक, और बंद होने के समय शाम 7:30 PM (जब तक कंपनी दूसरा समय न रखे) के बाद कभी नहीं।
- पंच-इन से पहले, पंच-आउट के बाद, छुट्टी के दिन या लीव पर कुछ दर्ज नहीं होता।
- पंच-इन रहते समय फ़ोन हर कुछ मिनट में आपकी लोकेशन जाँचता है। कितने समय में, यह कंपनी तय करती है।

## क्या दर्ज होता है
- जगह (अक्षांश-देशांतर), वह कितनी सटीक है, समय, और फ़ोन की बैटरी तथा चार्ज हो रहा है या नहीं।
- इसके अलावा कुछ नहीं: ऐप आवाज़, आपकी स्क्रीन, आपके संदेश, कॉन्टैक्ट या फ़ोटो रिकॉर्ड नहीं करता।

## कार्यस्थल और बाहर बिताया समय
- हर ऑफ़िस या फ़ैक्टरी की नक़्शे पर एक सीमा होती है। ऑफ़िस या फ़ैक्टरी वाले दिन आपकी लोकेशन की तुलना उससे होती है।
- यदि लगातार कई लोकेशन चेक आपको सीमा के बाहर दिखाएँ तो बाहर बिताया समय आपके घंटों में नहीं गिना जाता, और ऐप आपको बताता है। ऐप में शुरू की गई मीटिंग या ग्राहक विज़िट, फ़ील्ड वर्क, या ऐप में शुरू किया गया ज़रूरी निजी काम अलग रखा जाता है और "बाहर" नहीं माना जाता।
- यदि चेक बताएँ कि आप एक जगह से दूसरी जगह जा रहे हैं तो ऐप आपको "यात्रा में" दिखाता है और वह समय काम के घंटों में नहीं गिना जाता।

## लोकेशन बंद हो तो
- कृपया लोकेशन चालू रखें और ऐप को उसकी अनुमति दें। यदि आपका फ़ोन लोकेशन नहीं भेजता, तो ऐप पहले आपको संदेश भेजता है, सामान्यतः 45 मिनट बाद।
- 90 मिनट बाद भी बंद रहे तो आपके सुपरवाइज़र और कंपनी के एडमिन को ई-मेल से बताया जाता है।
- 120 मिनट बाद भी बंद रहे तो आपका दिन उस आख़िरी समय पर अपने-आप बंद हो जाता है जब आपकी लोकेशन मिली थी, और उस पर Auto लिखा जाता है। कंपनी ये समय बदल सकती है या इसे बंद कर सकती है।
- यदि आपका दिन ग़लती से बंद हो गया हो तो ऐप खोलकर "Resume work" दबाएँ। बीच का समय निजी काम के रूप में रखा जाता है।

## इसे कौन देख सकता है
आपके सुपरवाइज़र, विभाग के एडमिन और कंपनी के एडमिन या मालिक आपके लोकेशन चेक और दिन का रास्ता देख सकते हैं। आप देख सकते हैं कि आज कितने चेक हुए और आख़िरी जगह कौन-सी दर्ज हुई। दूसरे कर्मचारी आपकी लोकेशन नहीं देख सकते।

## कैसे उपयोग और कितने समय रखी जाती है
लोकेशन का उपयोग हाज़िरी, काम के घंटे, सुरक्षा और रिपोर्ट के लिए होता है। यह गोपनीयता नीति में बताए अनुसार रखी जाती है, बेची नहीं जाती और विज्ञापन के लिए उपयोग नहीं होती।

## आपका विकल्प
आप साइन-आउट करके मना कर सकते हैं; तब कंपनी बताएगी कि आपकी हाज़िरी कैसे दर्ज होगी। व्यक्तिगत डेटा का क़ानून आपको अपने डेटा के बारे में कंपनी से पूछने का अधिकार भी देता है। {{officer}}, {{email}}, {{phone}} पर लिखें।`
  });

  /* ────────────────────────── Attendance Rules ────────────────────────── */
  P.push({
    slug: 'attendance-rules', kind: 'hr', sort: 4, pub: 0, must: 0,
    title: 'Attendance Rules', titleHi: 'हाज़िरी के नियम',
    body: `## Working days
- Sunday is the weekly off. Saturday is a normal working day.
- National holidays are off unless {{company}} has cancelled one; the Holidays list in the app is the final word.
- Each department has an office start time. Punching in after the start time plus the grace minutes is marked Late. Your start time is shown on your Home screen.

## Punching in and out
- Punch in when you start work and punch out when you finish. Take the selfie with your face in the frame, in good light, and keep Location on. A punch without a face or without a location may be refused.
- When you punch in, choose your work mode: **Field Work**, **Office Work** or **Work From Home**. Office Work has to be done from an office location and Factory work from a factory location; the app checks your phone against it.
- Punch out only after you are back from a meeting or visit.

## Stepping out during work
- **Meeting or customer visit:** tap Going for meeting or Customer visit, and tap again when you are back. Enter the customer, the reason and the result.
- **Urgent personal work:** tap Urgent personal work and tap again when you return. That time is kept separate and is not counted as work.
- **Time away from your work place:** if several location checks in a row show you outside your work place, that time is not counted in your hours.
- **Site work:** stop site work before you go for a meeting.

## Location off
If your phone stops sending location, the app sends you a message, then tells your supervisor and admin, and may close your day automatically. The Location & Tracking Notice explains the times. If your day is closed by mistake, tap Resume work.

## Mistakes and corrections
Forgot to punch, or a wrong time? Use Fix a punch, say what happened, and your supervisor or admin decides. Please fix mistakes the same day.

## Overtime
Start and stop overtime in the app when you work extra. Your admin approves it, and only approved overtime counts.

## Honesty
Faking a location or a photo, or punching in for someone else, is a serious breach and is dealt with under the company's disciplinary rules.

## Questions
Ask your supervisor, or write to {{email}}.`,
    bodyHi: `## काम के दिन
- रविवार साप्ताहिक छुट्टी है। शनिवार सामान्य कामकाजी दिन है।
- राष्ट्रीय अवकाश छुट्टी होते हैं, जब तक {{company}} ने किसी को रद्द न किया हो; ऐप की छुट्टियों की सूची (Holidays) ही अंतिम है।
- हर विभाग का ऑफ़िस शुरू होने का समय है। शुरू होने के समय और ग्रेस मिनट के बाद पंच-इन करने पर "लेट" लिखा जाता है। आपका समय आपकी होम स्क्रीन पर दिखता है।

## पंच-इन और पंच-आउट
- काम शुरू करते समय पंच-इन और ख़त्म करते समय पंच-आउट करें। सेल्फ़ी में चेहरा फ़्रेम में और अच्छी रोशनी में रखें, और लोकेशन चालू रखें। बिना चेहरे या बिना लोकेशन के पंच मना किया जा सकता है।
- पंच-इन करते समय अपना वर्क मोड चुनें: **फ़ील्ड वर्क**, **ऑफ़िस वर्क** या **वर्क फ़्रॉम होम**। ऑफ़िस वर्क ऑफ़िस की जगह से और फ़ैक्टरी वर्क फ़ैक्टरी की जगह से करना होता है; ऐप आपके फ़ोन को उससे मिलाकर देखता है।
- मीटिंग या विज़िट से लौटने के बाद ही पंच-आउट करें।

## काम के बीच बाहर जाना
- **मीटिंग या ग्राहक विज़िट:** "Going for meeting" या "Customer visit" दबाएँ और लौटकर फिर दबाएँ। ग्राहक, कारण और नतीजा लिखें।
- **ज़रूरी निजी काम:** "Urgent personal work" दबाएँ और लौटकर फिर दबाएँ। वह समय अलग रखा जाता है और काम में नहीं गिना जाता।
- **कार्यस्थल से बाहर का समय:** यदि लगातार कई लोकेशन चेक आपको कार्यस्थल से बाहर दिखाएँ तो वह समय आपके घंटों में नहीं गिना जाता।
- **साइट वर्क:** मीटिंग पर जाने से पहले साइट वर्क रोकें।

## लोकेशन बंद हो तो
यदि आपका फ़ोन लोकेशन भेजना बंद कर दे तो ऐप पहले आपको संदेश भेजता है, फिर आपके सुपरवाइज़र और एडमिन को बताता है, और आपका दिन अपने-आप बंद भी कर सकता है। समय "लोकेशन और ट्रैकिंग सूचना" में लिखे हैं। दिन ग़लती से बंद हो जाए तो "Resume work" दबाएँ।

## ग़लती और सुधार
पंच करना भूल गए या समय ग़लत है? "पंच ठीक करें" का उपयोग करें, बताएँ क्या हुआ, और आपके सुपरवाइज़र या एडमिन तय करेंगे। कृपया ग़लती उसी दिन ठीक करवाएँ।

## ओवरटाइम
अतिरिक्त काम करने पर ऐप में ओवरटाइम शुरू और बंद करें। आपका एडमिन उसे मंज़ूर करता है, और केवल मंज़ूर ओवरटाइम गिना जाता है।

## ईमानदारी
नक़ली लोकेशन या फ़ोटो, या किसी और के लिए पंच-इन करना गंभीर उल्लंघन है और कंपनी के अनुशासन नियमों के अनुसार निपटाया जाता है।

## प्रश्न
अपने सुपरवाइज़र से पूछें, या {{email}} पर लिखें।`
  });

  /* ────────────────────────── Leave Rules ────────────────────────── */
  P.push({
    slug: 'leave-rules', kind: 'hr', sort: 5, pub: 0, must: 0,
    title: 'Leave Rules', titleHi: 'छुट्टी के नियम',
    body: `## Kinds of leave
- **CL, Casual Leave:** short leave for personal matters. It is earned every month, and unused days carry forward until the leave year ends.
- **VL, Vacation Leave:** planned holidays.
- **ML, Medical Leave:** for illness or injury.
How many days you have is shown under Leave balance in the app. [Add the yearly days for each kind here.]

## How to ask
Open Apply for leave in the app, choose the kind and the dates, and give a reason. Your supervisor or admin approves or declines it, and you get a message.

## Asking in time
- Casual leave has to be asked for at least 2 days before it starts.
- Vacation leave has to be asked for at least 1 month before it starts.
- The app shows the earliest first day you can pick. {{company}} can change these notice periods.

## Medical leave and documents
- Medical leave cannot be asked for in advance. Ask for it when you are ill, then upload your medical reports in the app within 7 days.
- Your admin can approve medical leave only after the documents are in. Until then the request shows "medical documents pending".
- Medical documents are kept only for a short time after the request is decided and are then deleted. The request still shows that the documents were received.

## Approved leave
- A day of approved leave is never marked absent.
- The team leave calendar shows everyone the names and days of leave. It never shows the reason or the kind.

## Leave without balance or approval
Days taken without balance or without approval are treated under the company's rules on leave without pay. [Say here how such days are treated.]

## Questions
Ask your supervisor, or write to {{email}}.`,
    bodyHi: `## छुट्टी के प्रकार
- **CL, कैज़ुअल लीव:** निजी काम के लिए छोटी छुट्टी। यह हर महीने जुड़ती है, और बची हुई छुट्टियाँ लीव वर्ष ख़त्म होने तक आगे बढ़ती हैं।
- **VL, वेकेशन लीव:** पहले से तय छुट्टियों के लिए।
- **ML, मेडिकल लीव:** बीमारी या चोट के लिए।
आपके पास कितने दिन हैं यह ऐप में Leave balance में दिखता है। [यहाँ हर प्रकार के सालाना दिन लिखें।]

## कैसे माँगें
ऐप में "Apply for leave" खोलें, प्रकार और तारीख़ें चुनें और कारण लिखें। आपके सुपरवाइज़र या एडमिन उसे मंज़ूर या अस्वीकार करते हैं, और आपको संदेश मिलता है।

## समय पर माँगना
- कैज़ुअल लीव शुरू होने से कम से कम 2 दिन पहले माँगनी होती है।
- वेकेशन लीव शुरू होने से कम से कम 1 महीना पहले माँगनी होती है।
- ऐप दिखाता है कि आप सबसे जल्दी किस तारीख़ से छुट्टी ले सकते हैं। {{company}} ये अवधियाँ बदल सकती है।

## मेडिकल लीव और दस्तावेज़
- मेडिकल लीव पहले से नहीं माँगी जा सकती। बीमार होने पर माँगें, फिर 7 दिन के भीतर ऐप में अपने मेडिकल कागज़ अपलोड करें।
- आपका एडमिन मेडिकल लीव तभी मंज़ूर कर सकता है जब दस्तावेज़ आ जाएँ। तब तक आवेदन पर "medical documents pending" दिखता है।
- मेडिकल दस्तावेज़ आवेदन तय होने के बाद थोड़े समय ही रखे जाते हैं, फिर हटा दिए जाते हैं। आवेदन में फिर भी दिखता है कि दस्तावेज़ मिल गए थे।

## मंज़ूर छुट्टी
- मंज़ूर छुट्टी के दिन को कभी अनुपस्थित नहीं लिखा जाता।
- टीम का छुट्टी कैलेंडर सबको छुट्टी वाले लोगों के नाम और दिन दिखाता है। कारण या प्रकार कभी नहीं दिखाता।

## बिना बैलेंस या बिना मंज़ूरी की छुट्टी
बिना बैलेंस या बिना मंज़ूरी लिए गए दिन कंपनी के बिना वेतन छुट्टी के नियमों के अनुसार देखे जाते हैं। [यहाँ लिखें कि ऐसे दिनों का क्या होता है।]

## प्रश्न
अपने सुपरवाइज़र से पूछें, या {{email}} पर लिखें।`
  });

  /* ────────────────────────── Travelling Rules ────────────────────────── */
  P.push({
    slug: 'travel-rules', kind: 'hr', sort: 6, pub: 0, must: 0,
    title: 'Travelling Rules', titleHi: 'यात्रा के नियम',
    body: `## Travelling between places
- If your location checks show you moving between offices, factories or sites during the day, the app shows you as **Travelling**, and that time is not counted in your working hours.
- You do not need to start anything for this. When you reach your work place the app goes back to showing you as working.

## Field work, meetings and customer visits
- Meetings and customer visits you start in the app are work, not "away". Tap Going for meeting or Customer visit when you leave, add the customer's name and phone, and tap again when you are back.
- Take the photo and write short notes at each visit. Several visits in one day are fine; each one is recorded in order.
- On Field Work days you are not held to an office boundary, but your location is still recorded between punch-in and punch-out.

## Distance and travel reports
The app works out the distance you travelled from your location checks and shows it in the travel report for your supervisor and admin. Keep Location on while you travel so the distance is correct.

## Travel costs
Petrol, fare and other travel costs are paid at [rate or rules set by the company]. Claim them against the visit you made, with the details asked in the app. Only genuine travel for company work is paid.

## Safety
If you are hurt, in danger or need help while travelling, tap the SOS button. It sends your location to your supervisor and the admin.

## Questions
Ask your supervisor, or write to {{email}}.`,
    bodyHi: `## एक जगह से दूसरी जगह जाना
- यदि आपके लोकेशन चेक बताएँ कि आप दिन में ऑफ़िस, फ़ैक्टरी या साइट के बीच जा रहे हैं तो ऐप आपको **यात्रा में (Travelling)** दिखाता है, और वह समय आपके काम के घंटों में नहीं गिना जाता।
- इसके लिए आपको कुछ शुरू नहीं करना होता। जब आप अपने कार्यस्थल पहुँचते हैं तो ऐप फिर आपको काम करता हुआ दिखाता है।

## फ़ील्ड वर्क, मीटिंग और ग्राहक विज़िट
- ऐप में शुरू की गई मीटिंग और ग्राहक विज़िट काम हैं, "बाहर" नहीं। निकलते समय "Going for meeting" या "Customer visit" दबाएँ, ग्राहक का नाम और फ़ोन लिखें, और लौटकर फिर दबाएँ।
- हर विज़िट पर फ़ोटो लें और छोटे नोट लिखें। एक दिन में कई विज़िट ठीक हैं; हर एक क्रम से दर्ज होती है।
- फ़ील्ड वर्क के दिन आप ऑफ़िस की सीमा से बँधे नहीं होते, पर पंच-इन से पंच-आउट तक आपकी लोकेशन फिर भी दर्ज होती है।

## दूरी और यात्रा रिपोर्ट
ऐप आपके लोकेशन चेक से तय की गई दूरी निकालता है और आपके सुपरवाइज़र और एडमिन की यात्रा रिपोर्ट में दिखाता है। यात्रा के दौरान लोकेशन चालू रखें ताकि दूरी सही आए।

## यात्रा का ख़र्च
पेट्रोल, किराया और यात्रा के अन्य ख़र्च [कंपनी की तय दर या नियम] के अनुसार दिए जाते हैं। उन्हें अपनी की हुई विज़िट के साथ, ऐप में माँगे गए विवरण सहित, क्लेम करें। केवल कंपनी के काम की असली यात्रा का भुगतान होता है।

## सुरक्षा
यात्रा में चोट लगे, ख़तरा हो या मदद चाहिए तो SOS बटन दबाएँ। वह आपकी लोकेशन आपके सुपरवाइज़र और एडमिन को भेजता है।

## प्रश्न
अपने सुपरवाइज़र से पूछें, या {{email}} पर लिखें।`
  });

  return P;
});
