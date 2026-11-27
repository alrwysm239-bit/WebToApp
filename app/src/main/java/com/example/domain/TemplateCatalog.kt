package com.example.domain

import com.example.data.model.TemplateItem

object TemplateCatalog {

    val allTemplates: List<TemplateItem> by lazy {
        listOf(
            TemplateItem(
                id = "landing_saas",
                categoryAr = "أعمال", categoryEn = "Business",
                titleAr = "صفحة هبوط SaaS", titleEn = "SaaS Landing Page",
                descriptionAr = "صفحة تعريفية متجاوبة بتدرجات حديثة وقسم مميزات",
                descriptionEn = "Modern hero landing page with features & CTA",
                accentHex = 0xFF5B3DF5,
                html = landingTemplate("منصة سحابية ذكية", "أطلق مشروعك خلال دقائق بسرعة وأمان.", "ابدأ مجاناً", listOf("سرعة فائقة", "حماية متكاملة", "تحليلات فورية")),
            ),
            TemplateItem(
                id = "portfolio_dev",
                categoryAr = "شخصي", categoryEn = "Personal",
                titleAr = "معرض أعمال مبدع", titleEn = "Creative Portfolio",
                descriptionAr = "عرض المشاريع والمهارات مع فلترة تفاعلية",
                descriptionEn = "Project showcase with interactive category filter",
                accentHex = 0xFF00C6FF,
                html = portfolioTemplate(),
            ),
            TemplateItem(
                id = "ecommerce_store",
                categoryAr = "تجارة", categoryEn = "Commerce",
                titleAr = "متجر إلكتروني مع سلة", titleEn = "E-Commerce Store",
                descriptionAr = "متجر منتجات مع سلة تسوق حية وحساب الإجمالي",
                descriptionEn = "Product catalog with live shopping cart",
                accentHex = 0xFF34E0A1,
                html = storeTemplate(),
            ),
            TemplateItem(
                id = "blog_editorial",
                categoryAr = "محتوى", categoryEn = "Content",
                titleAr = "مدونة ومجلة رقمية", titleEn = "Editorial Blog",
                descriptionAr = "مقالات أنيقة مع شريط بحث وتصفية فورية",
                descriptionEn = "Clean article feed with instant search",
                accentHex = 0xFF9B7BFF,
                html = blogTemplate(),
            ),
            TemplateItem(
                id = "resume_cv",
                categoryAr = "شخصي", categoryEn = "Personal",
                titleAr = "سيرة ذاتية احترافية", titleEn = "Interactive CV",
                descriptionAr = "سيرة ذاتية جاهزة للطباعة والنسخ والتواصل",
                descriptionEn = "Modern print-ready resume & skills profile",
                accentHex = 0xFF5B3DF5,
                html = cvTemplate(),
            ),
            TemplateItem(
                id = "memory_game",
                categoryAr = "ألعاب", categoryEn = "Games",
                titleAr = "لعبة الذاكرة التفاعلية", titleEn = "Memory Match Game",
                descriptionAr = "لعبة بطاقات كاملة بحساب الحركات وإعادة اللعب",
                descriptionEn = "Playable card-matching game with move counter",
                accentHex = 0xFFFFB84D,
                html = memoryGameTemplate(),
            ),
            TemplateItem(
                id = "notes_app",
                categoryAr = "تطبيقات", categoryEn = "Apps",
                titleAr = "تطبيق ملاحظات محلي", titleEn = "Notes & Tasks App",
                descriptionAr = "إضافة وحذف وحفظ الملاحظات في localStorage",
                descriptionEn = "Persistent notes & checklist with localStorage",
                accentHex = 0xFF34E0A1,
                html = notesAppTemplate(),
            ),
            TemplateItem(
                id = "restaurant_menu",
                categoryAr = "تجارة", categoryEn = "Commerce",
                titleAr = "قائمة مطعم وكافيه", titleEn = "Restaurant QR Menu",
                descriptionAr = "قائمة طعام جذابة مع تصنيفات وطلب واتساب",
                descriptionEn = "Categorized digital menu with quick order",
                accentHex = 0xFFFF5C7C,
                html = landingTemplate("مطعم نكهة الشرق", "أشهى الأطباق الطازجة يومياً بتوصيل سريع.", "اطلب الآن", listOf("مشويات خاصة", "سلطات طازجة", "حلويات شرقية")),
            ),
            TemplateItem(
                id = "pricing_tiers",
                categoryAr = "أعمال", categoryEn = "Business",
                titleAr = "جدول باقات وأسعار", titleEn = "SaaS Pricing Cards",
                descriptionAr = "مقارنة خطط الاشتراك مع تبديل شهري/سنوي",
                descriptionEn = "Monthly/Yearly toggle pricing comparison",
                accentHex = 0xFF00C6FF,
                html = pricingTemplate(),
            ),
            TemplateItem(
                id = "coming_soon",
                categoryAr = "أعمال", categoryEn = "Business",
                titleAr = "قريباً مع عداد تنازلي", titleEn = "Coming Soon Countdown",
                descriptionAr = "صفحة إطلاق منتج مع عداد حي وتسجيل بريد",
                descriptionEn = "Product launch countdown with waitlist signup",
                accentHex = 0xFF9B7BFF,
                html = countdownTemplate(),
            ),
            TemplateItem(
                id = "bio_links",
                categoryAr = "شخصي", categoryEn = "Personal",
                titleAr = "صفحة روابط السوشيال", titleEn = "Link-in-Bio Hub",
                descriptionAr = "بطاقة تعريفية تجمع كل حساباتك وروابطك",
                descriptionEn = "Sleek social link hub for creators",
                accentHex = 0xFF5B3DF5,
                html = bioLinksTemplate(),
            ),
            TemplateItem(
                id = "digital_agency",
                categoryAr = "أعمال", categoryEn = "Business",
                titleAr = "وكالة تسويق وبرمجة", titleEn = "Digital Agency",
                descriptionAr = "عرض خدمات الوكالة ونموذج تواصل مباشر",
                descriptionEn = "Agency showcase with contact form",
                accentHex = 0xFF00C6FF,
                html = landingTemplate("وكالة نبض الرقمية", "نصمم الهويات والمواقع والتطبيقات بمعايير عالمية.", "احجز استشارة", listOf("تصميم UI/UX", "تطوير ويب وتطبيقات", "نمو وتسويق رقمي")),
            ),
            TemplateItem(
                id = "analytics_dash",
                categoryAr = "تطبيقات", categoryEn = "Apps",
                titleAr = "لوحة تحكم إحصائيات", titleEn = "Analytics Dashboard",
                descriptionAr = "بطاقات مؤشرات KPI ورسوم بيانية تفاعلية",
                descriptionEn = "KPI cards & animated SVG bar chart",
                accentHex = 0xFF34E0A1,
                html = dashboardTemplate(),
            ),
            TemplateItem(
                id = "calculator_app",
                categoryAr = "تطبيقات", categoryEn = "Apps",
                titleAr = "آلة حاسبة أنيقة", titleEn = "Modern Calculator",
                descriptionAr = "آلة حاسبة عملية بتصميم زجاجي داكن",
                descriptionEn = "Working dark glassmorphic calculator",
                accentHex = 0xFF00C6FF,
                html = calculatorTemplate(),
            ),
            TemplateItem(
                id = "quiz_app",
                categoryAr = "ألعاب", categoryEn = "Games",
                titleAr = "تطبيق اختبارات تفاعلي", titleEn = "Interactive Quiz",
                descriptionAr = "أسئلة متعددة الخيارات مع النتيجة الفورية",
                descriptionEn = "Multi-question quiz with live score",
                accentHex = 0xFF5B3DF5,
                html = quizTemplate(),
            ),
            TemplateItem(
                id = "podcast_player",
                categoryAr = "محتوى", categoryEn = "Content",
                titleAr = "منصة بودكاست وصوتيات", titleEn = "Podcast Hub",
                descriptionAr = "قائمة حلقات مع مشغل تفاعلي",
                descriptionEn = "Episode list with interactive audio controls",
                accentHex = 0xFFFF5C7C,
                html = landingTemplate("بودكاست تقنية المستقبل", "حوارات أسبوعية مع رواد التقنية والذكاء الاصطناعي.", "استمع للحلقة", listOf("الحلقة 12: مستقبل الويب", "الحلقة 11: تصميم التطبيقات", "الحلقة 10: الذكاء التوليدي")),
            ),
            TemplateItem(
                id = "real_estate",
                categoryAr = "تجارة", categoryEn = "Commerce",
                titleAr = "بوابة عقارات فاخرة", titleEn = "Real Estate Showcase",
                descriptionAr = "عرض وحدات سكنية مع الأسعار والمواصفات",
                descriptionEn = "Luxury property cards & inquiry CTA",
                accentHex = 0xFFFFB84D,
                html = landingTemplate("أفق العقارية", "اكتشف أرقى الفلل والشقق الذكية في قلب المدينة.", "استعرض الوحدات", listOf("فيلا الياسمين - 450م", "شقة البانوراما - 220م", "برج الأعمال الذكي")),
            ),
            TemplateItem(
                id = "medical_clinic",
                categoryAr = "أعمال", categoryEn = "Business",
                titleAr = "عيادة ومركز طبي", titleEn = "Medical Clinic",
                descriptionAr = "التخصصات الطبية وحجز المواعيد أونلاين",
                descriptionEn = "Clinic services & appointment booking",
                accentHex = 0xFF34E0A1,
                html = landingTemplate("مركز الشفاء الطبي", "رعاية صحية متكاملة بأحدث الأجهزة الطبية.", "احجز موعدك", listOf("عيادة الباطنية", "طب الأسنان التجميلي", "المختبر والأشعة")),
            ),
            TemplateItem(
                id = "fitness_tracker",
                categoryAr = "تطبيقات", categoryEn = "Apps",
                titleAr = "متتبع لياقة وتمارين", titleEn = "Fitness Tracker",
                descriptionAr = "حساب السعرات وتسجيل التمارين اليومية",
                descriptionEn = "Daily workout & water intake tracker",
                accentHex = 0xFF34E0A1,
                html = fitnessTemplate(),
            ),
            TemplateItem(
                id = "tech_event",
                categoryAr = "محتوى", categoryEn = "Content",
                titleAr = "مؤتمر وفعالية تقنية", titleEn = "Tech Conference",
                descriptionAr = "جدول المتحدثين وحجز التذاكر",
                descriptionEn = "Event schedule & speaker highlights",
                accentHex = 0xFF5B3DF5,
                html = landingTemplate("قمة الابتكار 2026", "ثلاثة أيام من ورش العمل واللقاءات التقنية.", "سجل حضورك", listOf("اليوم الأول: الذكاء الاصطناعي", "اليوم الثاني: تطبيقات الجوال", "اليوم الثالث: ريادة الأعمال")),
            ),
            TemplateItem(
                id = "photo_gallery",
                categoryAr = "شخصي", categoryEn = "Personal",
                titleAr = "معرض صور بتصميم شبكي", titleEn = "Masonry Gallery",
                descriptionAr = "معرض صور متجاوب مع نافذة تكبير Lightbox",
                descriptionEn = "Responsive visual grid with modal viewer",
                accentHex = 0xFF9B7BFF,
                html = galleryTemplate(),
            ),
            TemplateItem(
                id = "api_docs",
                categoryAr = "محتوى", categoryEn = "Content",
                titleAr = "توثيق تقني و API Docs", titleEn = "Developer Docs",
                descriptionAr = "صفحة توثيق نظيفة مع أمثلة كود جاهزة للنسخ",
                descriptionEn = "Clean developer docs with copyable snippets",
                accentHex = 0xFF00C6FF,
                html = docsTemplate(),
            ),
        )
    }

    private fun baseShell(title: String, body: String, extraCss: String = "", extraJs: String = ""): String = """
        <!DOCTYPE html>
        <html lang="ar" dir="rtl">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>$title</title>
          <style>
            * { box-sizing: border-box; margin: 0; padding: 0; }
            body { font-family: system-ui, -apple-system, sans-serif; background: #0C0A20; color: #F4F6FF; line-height: 1.65; }
            .wrap { max-width: 960px; margin: 0 auto; padding: 24px 18px; }
            .card { background: #1C1840; border: 1px solid #332D6E; border-radius: 20px; padding: 20px; }
            .btn { border: none; border-radius: 14px; padding: 12px 20px; font-weight: 700; color: #fff; cursor: pointer; background: linear-gradient(90deg, #5B3DF5, #00C6FF); }
            .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 14px; }
            .muted { color: #A8AED8; font-size: 14px; }
            $extraCss
          </style>
        </head>
        <body>
          <div class="wrap">$body</div>
          <script>$extraJs</script>
        </body>
        </html>
    """.trimIndent()

    private fun landingTemplate(title: String, subtitle: String, cta: String, features: List<String>): String = baseShell(
        title = title,
        body = """
            <header class="card" style="text-align:center;padding:36px 20px;margin-bottom:18px;border-color:#5B3DF5;">
              <h1 style="font-size:28px;margin-bottom:10px;">$title</h1>
              <p class="muted" style="margin-bottom:20px;">$subtitle</p>
              <button class="btn" onclick="alert('$cta ✨')">$cta</button>
            </header>
            <section class="grid">
              ${features.joinToString("") { "<div class='card'><h3 style='color:#00C6FF;margin-bottom:6px;'>$it</h3><p class='muted'>جودة عالية وتجربة استخدام متجاوبة لجميع الشاشات.</p></div>" }}
            </section>
        """.trimIndent(),
    )

    private fun portfolioTemplate(): String = baseShell(
        title = "معرض الأعمال",
        body = """
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;flex-wrap:wrap;gap:10px;">
              <h2>أعمالي المختارة</h2>
              <div style="display:flex;gap:8px;">
                <button class="btn" onclick="filterP('all')">الكل</button>
                <button class="btn" style="background:#1C1840;border:1px solid #5B3DF5" onclick="filterP('web')">ويب</button>
                <button class="btn" style="background:#1C1840;border:1px solid #5B3DF5" onclick="filterP('app')">تطبيق</button>
              </div>
            </div>
            <div class="grid">
              <div class="card item" data-cat="web"><h3>منصة متجر</h3><p class="muted">واجهة تجارة إلكترونية حديثة</p></div>
              <div class="card item" data-cat="app"><h3>تطبيق مهام</h3><p class="muted">تطبيق إنتاجية سريع</p></div>
              <div class="card item" data-cat="web"><h3>موقع شركة</h3><p class="muted">هوية بصرية متكاملة</p></div>
            </div>
        """.trimIndent(),
        extraJs = "function filterP(c){document.querySelectorAll('.item').forEach(el=>{el.style.display=(c==='all'||el.dataset.cat===c)?'block':'none';});}",
    )

    private fun storeTemplate(): String = baseShell(
        title = "متجر WebForge",
        body = """
            <div class="card" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
              <h2>🛍️ المتجر الرقمي</h2>
              <span style="color:#34E0A1;font-weight:700;">السلة: <span id="cartCount">0</span> (<span id="cartTotal">0</span>$)</span>
            </div>
            <div class="grid">
              <div class="card"><h3>سماعة ذكية</h3><p class="muted">49$</p><br><button class="btn" onclick="add(49)">إضافة للسلة</button></div>
              <div class="card"><h3>ساعة رياضية</h3><p class="muted">89$</p><br><button class="btn" onclick="add(89)">إضافة للسلة</button></div>
              <div class="card"><h3>حقيبة لابتوب</h3><p class="muted">35$</p><br><button class="btn" onclick="add(35)">إضافة للسلة</button></div>
            </div>
        """.trimIndent(),
        extraJs = "let cnt=0,tot=0;function add(p){cnt++;tot+=p;document.getElementById('cartCount').textContent=cnt;document.getElementById('cartTotal').textContent=tot;}",
    )

    private fun blogTemplate(): String = baseShell(
        title = "المدونة التقنية",
        body = """
            <h1 style="margin-bottom:12px;">✍️ المدونة</h1>
            <input id="q" oninput="search()" placeholder="ابحث في المقالات..." style="width:100%;padding:12px 16px;border-radius:14px;border:1px solid #332D6E;background:#1C1840;color:#fff;margin-bottom:16px;">
            <div class="grid" id="posts">
              <article class="card post"><h3>أسرار تصميم واجهات الموبايل</h3><p class="muted">دليل عملي لتجربة مستخدم سلسة.</p></article>
              <article class="card post"><h3>تحسين سرعة المواقع والسيو</h3><p class="muted">خطوات بسيطة لرفع ترتيب موقعك.</p></article>
              <article class="card post"><h3>بناء تطبيقات PWA</h3><p class="muted">اجعل موقعك قابلاً للتثبيت.</p></article>
            </div>
        """.trimIndent(),
        extraJs = "function search(){let v=document.getElementById('q').value.toLowerCase();document.querySelectorAll('.post').forEach(p=>{p.style.display=p.textContent.toLowerCase().includes(v)?'block':'none';});}",
    )

    private fun cvTemplate(): String = baseShell(
        title = "السيرة الذاتية",
        body = """
            <div class="card" style="margin-bottom:14px;">
              <h1>أحمد السالم — مطور ويب وتطبيقات</h1>
              <p class="muted">خبرة +5 سنوات في بناء الواجهات الحديثة وتجربة المستخدم.</p>
            </div>
            <div class="grid">
              <div class="card"><h3>المهارات</h3><p class="muted">HTML5, CSS3, JavaScript, React, Kotlin, UI/UX</p></div>
              <div class="card"><h3>التواصل</h3><p class="muted">hello@example.com • الرياض</p></div>
            </div>
        """.trimIndent(),
    )

    private fun memoryGameTemplate(): String = baseShell(
        title = "لعبة الذاكرة",
        body = """
            <div class="card" style="text-align:center;">
              <h2>🎮 لعبة مطابقة الرموز</h2>
              <p class="muted">المحاولات: <span id="moves">0</span></p><br>
              <div id="board" style="display:grid;grid-template-columns:repeat(4,1fr);gap:10px;max-width:340px;margin:0 auto;"></div><br>
              <button class="btn" onclick="initGame()">إعادة اللعب</button>
            </div>
        """.trimIndent(),
        extraCss = ".tile{height:68px;border-radius:14px;background:#252054;border:1.5px solid #5B3DF5;font-size:26px;color:#fff;cursor:pointer;}",
        extraJs = """
            const icons=['🚀','⚡','🎨','💎','🚀','⚡','🎨','💎'];let open=[],moves=0,lock=false;
            function initGame(){moves=0;open=[];lock=false;document.getElementById('moves').textContent=0;const b=document.getElementById('board');b.innerHTML='';[...icons].sort(()=>Math.random()-0.5).forEach(ic=>{const btn=document.createElement('button');btn.className='tile';btn.dataset.v=ic;btn.textContent='?';btn.onclick=()=>{if(lock||btn.textContent!=='?')return;btn.textContent=ic;open.push(btn);if(open.length===2){moves++;document.getElementById('moves').textContent=moves;if(open[0].dataset.v!==open[1].dataset.v){lock=true;setTimeout(()=>{open.forEach(x=>x.textContent='?');open=[];lock=false;},650);}else{open=[];}}};b.appendChild(btn);});}initGame();
        """.trimIndent(),
    )

    private fun notesAppTemplate(): String = baseShell(
        title = "تطبيق الملاحظات",
        body = """
            <div class="card">
              <h2>📝 ملاحظاتي السريعة</h2><br>
              <div style="display:flex;gap:8px;margin-bottom:14px;">
                <input id="noteIn" placeholder="اكتب ملاحظة..." style="flex:1;padding:12px;border-radius:12px;border:1px solid #332D6E;background:#0C0A20;color:#fff;">
                <button class="btn" onclick="addNote()">إضافة</button>
              </div>
              <ul id="list" style="list-style:none;display:flex;flex-direction:column;gap:8px;"></ul>
            </div>
        """.trimIndent(),
        extraJs = """
            let notes=JSON.parse(localStorage.getItem('wf_notes')||'["تجهيز التصميم","نشر الموقع"]');
            function render(){localStorage.setItem('wf_notes',JSON.stringify(notes));document.getElementById('list').innerHTML=notes.map((n,i)=>`<li style="background:#0C0A20;padding:10px 14px;border-radius:12px;display:flex;justify-content:space-between;"><span>${'$'}{n}</span><button onclick="notes.splice(${'$'}{i},1);render()" style="background:none;border:none;color:#FF5C7C;cursor:pointer;">✕</button></li>`).join('');}
            function addNote(){const v=document.getElementById('noteIn').value.trim();if(!v)return;notes.unshift(v);document.getElementById('noteIn').value='';render();}render();
        """.trimIndent(),
    )

    private fun pricingTemplate(): String = baseShell(
        title = "الباقات والأسعار",
        body = """
            <div style="text-align:center;margin-bottom:18px;">
              <h2>اختر خطتك المناسبة</h2><br>
              <button class="btn" onclick="togglePlan()">تبديل شهري / سنوي (وفر 20%)</button>
            </div>
            <div class="grid">
              <div class="card"><h3>المجانية</h3><h2 style="color:#34E0A1;">0$</h2><p class="muted">مشاريع غير محدودة محلياً</p></div>
              <div class="card" style="border-color:#00C6FF;"><h3>الاحترافية</h3><h2 id="proPrice" style="color:#00C6FF;">12$ / شهر</h2><p class="muted">نشر سحابي ودومين مخصص</p></div>
            </div>
        """.trimIndent(),
        extraJs = "let yearly=false;function togglePlan(){yearly=!yearly;document.getElementById('proPrice').textContent=yearly?'115$ / سنة':'12$ / شهر';}",
    )

    private fun countdownTemplate(): String = baseShell(
        title = "قريباً",
        body = """
            <div class="card" style="text-align:center;padding:40px 20px;">
              <h1 style="color:#00C6FF;">🚀 الإطلاق الرسمي قريباً</h1>
              <p class="muted" style="margin:10px 0 20px;">نعمل على تجربة مذهلة تستحق الانتظار.</p>
              <h2 id="timer" style="font-size:32px;color:#34E0A1;">00:00:00</h2>
            </div>
        """.trimIndent(),
        extraJs = "let s=86400;setInterval(()=>{s--;let h=Math.floor(s/3600),m=Math.floor((s%3600)/60),sc=s%60;document.getElementById('timer').textContent=[h,m,sc].map(x=>String(x).padStart(2,'0')).join(':');},1000);",
    )

    private fun bioLinksTemplate(): String = baseShell(
        title = "روابطي",
        body = """
            <div class="card" style="max-width:420px;margin:0 auto;text-align:center;">
              <div style="width:76px;height:76px;border-radius:50%;background:linear-gradient(135deg,#5B3DF5,#00C6FF);margin:0 auto 12px;display:flex;align-items:center;justify-content:center;font-size:28px;">✨</div>
              <h2>سارة التقنية</h2><p class="muted" style="margin-bottom:18px;">مصممة ومطورة واجهات</p>
              <div style="display:flex;flex-direction:column;gap:10px;">
                <a class="btn" style="text-decoration:none;" href="#portfolio">معرض أعمالي</a>
                <a class="btn" style="text-decoration:none;background:#252054;" href="#youtube">قناتي على يوتيوب</a>
                <a class="btn" style="text-decoration:none;background:#252054;" href="#contact">تواصل معي</a>
              </div>
            </div>
        """.trimIndent(),
    )

    private fun dashboardTemplate(): String = baseShell(
        title = "لوحة الإحصائيات",
        body = """
            <h2 style="margin-bottom:14px;">📊 ملخص الأداء</h2>
            <div class="grid" style="margin-bottom:16px;">
              <div class="card"><span class="muted">الزيارات</span><h2 style="color:#00C6FF;">24,580</h2></div>
              <div class="card"><span class="muted">التحويلات</span><h2 style="color:#34E0A1;">+18.4%</h2></div>
              <div class="card"><span class="muted">المستخدمون النشطون</span><h2 style="color:#9B7BFF;">1,420</h2></div>
            </div>
        """.trimIndent(),
    )

    private fun calculatorTemplate(): String = baseShell(
        title = "الحاسبة",
        body = """
            <div class="card" style="max-width:320px;margin:0 auto;">
              <input id="disp" readonly value="0" style="width:100%;padding:14px;font-size:24px;text-align:left;border-radius:12px;border:1px solid #332D6E;background:#0C0A20;color:#34E0A1;margin-bottom:12px;">
              <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;">
                ${listOf("7","8","9","/","4","5","6","*","1","2","3","-","C","0","=","+").joinToString("") {
                    "<button class='btn' style='padding:14px;' onclick=\"press('$it')\">$it</button>"
                }}
              </div>
            </div>
        """.trimIndent(),
        extraJs = "let d=document.getElementById('disp');function press(k){if(k==='C')d.value='0';else if(k==='='){try{d.value=Function('return '+d.value)()}catch{d.value='Err'}}else d.value=(d.value==='0'?k:d.value+k);}",
    )

    private fun quizTemplate(): String = baseShell(
        title = "اختبار معرفي",
        body = """
            <div class="card" style="max-width:480px;margin:0 auto;text-align:center;">
              <h2 id="qText">ما الوسم الصحيح لإنشاء رابط في HTML؟</h2><br>
              <div style="display:flex;flex-direction:column;gap:8px;">
                <button class="btn" onclick="ans(true)">&lt;a href="..."&gt;</button>
                <button class="btn" style="background:#252054;" onclick="ans(false)">&lt;link-url&gt;</button>
              </div>
              <p id="res" style="margin-top:14px;font-weight:700;"></p>
            </div>
        """.trimIndent(),
        extraJs = "function ans(ok){const r=document.getElementById('res');r.style.color=ok?'#34E0A1':'#FF5C7C';r.textContent=ok?'إجابة صحيحة! 🎉':'حاول مرة أخرى!';}",
    )

    private fun fitnessTemplate(): String = baseShell(
        title = "متتبع اللياقة",
        body = """
            <div class="card" style="text-align:center;max-width:420px;margin:0 auto;">
              <h2>💧 متتبع شرب الماء</h2>
              <h1 id="cups" style="font-size:42px;color:#00C6FF;margin:14px 0;">0 / 8</h1>
              <button class="btn" onclick="drink()">أضف كوب ماء +1</button>
            </div>
        """.trimIndent(),
        extraJs = "let c=0;function drink(){if(c<8)c++;document.getElementById('cups').textContent=c+' / 8';}",
    )

    private fun galleryTemplate(): String = baseShell(
        title = "معرض الصور",
        body = """
            <h2 style="margin-bottom:14px;">🖼️ معرض الألوان والتصاميم</h2>
            <div class="grid">
              <div class="card" style="height:140px;background:linear-gradient(135deg,#5B3DF5,#00C6FF);"></div>
              <div class="card" style="height:140px;background:linear-gradient(135deg,#34E0A1,#00C6FF);"></div>
              <div class="card" style="height:140px;background:linear-gradient(135deg,#FF5C7C,#9B7BFF);"></div>
            </div>
        """.trimIndent(),
    )

    private fun docsTemplate(): String = baseShell(
        title = "التوثيق البرمجي",
        body = """
            <div class="card">
              <h1 style="color:#00C6FF;">📘 WebForge API Docs</h1>
              <p class="muted">نقطة النهاية الرئيسية لإرسال البيانات:</p><br>
              <pre style="background:#0C0A20;padding:14px;border-radius:12px;direction:ltr;color:#34E0A1;overflow:auto;">curl -X POST https://api.example.com/v1/deploy</pre>
            </div>
        """.trimIndent(),
    )
}
