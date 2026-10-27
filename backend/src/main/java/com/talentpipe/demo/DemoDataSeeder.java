package com.talentpipe.demo;

import com.talentpipe.application.dto.UpdateApplicationStatusRequest;
import com.talentpipe.application.entity.ApplicationStatus;
import com.talentpipe.application.service.ApplicationService;
import com.talentpipe.auth.dto.RoleRequest;
import com.talentpipe.auth.entity.InvitationToken;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.InvitationTokenRepository;
import com.talentpipe.auth.repository.TenantRoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.auth.service.TenantRoleService;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.candidate.validator.CandidateNicValidator;
import com.talentpipe.common.util.SecureTokens;
import com.talentpipe.job.dto.HiringTeamRequest;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.job.entity.WorkplaceType;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.job.service.VacancyHiringTeamService;
import com.talentpipe.job.service.VacancyViewer;
import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.entity.StageType;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import com.talentpipe.security.Permission;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.dto.UpdateTenantSettingsRequest;
import com.talentpipe.tenant.service.TenantService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fills the database with a believable demo: three companies with their
 * teams, roles, profiles, vacancies in every state, hiring teams, pipeline
 * templates, candidates and applications. Every login is listed in
 * {@code docs/DEMO.md}; they all share the password set by
 * {@code talentpipe.demo.password} ({@code application-demo.yml}).
 *
 * <p><strong>Only with the {@code demo} profile</strong>
 * ({@code SPRING_PROFILES_ACTIVE=demo}), and only once: if the first demo
 * company already exists it does nothing. Never enable it in production.</p>
 *
 * <p>Goes through the real services wherever one exists, so the data obeys
 * the same rules as anything a user creates: profiles are normalized,
 * vacancies are validated, published and closed through their lifecycle,
 * custom roles are created (and audited) by the Owner. People are saved
 * directly — signing up and accepting an invitation need an inbox. A demo
 * seeder is the one place allowed to reach into several modules at once.</p>
 *
 * <p>All addresses use the reserved {@code .test} domain, so nothing can
 * ever be emailed to a real person.</p>
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** The first company seeded; its presence means the demo is already loaded. */
    static final String MARKER_SUBDOMAIN = "lumora";

    /** In two companies and a candidate too, with one password: signs in through the picker. */
    static final String CONSULTANT_EMAIL = "sanduni.karunaratne@talentconsult.test";

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final String LKR = "LKR — Sri Lankan rupee";
    private static final String COLOMBO_CITY = "Colombo";
    private static final String SRI_LANKA = "Sri Lanka";
    private static final String DEGREE_IT = "BSc in Computer Science, Software Engineering or a related field";
    private static final String RIGHT_TO_WORK = "Do you have the right to work in Sri Lanka?";
    private static final String NOTICE = "What is your notice period?";

    private final TenantService tenantService;
    private final TenantRoleService tenantRoleService;
    private final TenantRoleRepository tenantRoleRepository;
    private final UserRepository userRepository;
    private final InvitationTokenRepository invitationTokenRepository;
    private final CandidateRepository candidateRepository;
    private final JobVacancyService vacancyService;
    private final VacancyHiringTeamService hiringTeamService;
    private final PipelineTemplateService pipelineTemplateService;
    private final ApplicationService applicationService;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    /** One password for every demo account; the workspace picker needs the same one anyway. */
    private final String demoPassword;

    /** What the seed creates, for the backdating step that follows it. */
    private final List<Posted> posted = new ArrayList<>();
    private final List<Applied> applied = new ArrayList<>();
    private String passwordHash;
    private LocalDate today;

    @SuppressWarnings("java:S107") // a seeder touches every module by design
    public DemoDataSeeder(TenantService tenantService,
                          TenantRoleService tenantRoleService,
                          TenantRoleRepository tenantRoleRepository,
                          UserRepository userRepository,
                          InvitationTokenRepository invitationTokenRepository,
                          CandidateRepository candidateRepository,
                          JobVacancyService vacancyService,
                          VacancyHiringTeamService hiringTeamService,
                          PipelineTemplateService pipelineTemplateService,
                          ApplicationService applicationService,
                          PasswordEncoder passwordEncoder,
                          JdbcTemplate jdbc,
                          PlatformTransactionManager transactionManager,
                          @Value("${talentpipe.demo.password}") String demoPassword) {
        this.tenantService = tenantService;
        this.tenantRoleService = tenantRoleService;
        this.tenantRoleRepository = tenantRoleRepository;
        this.userRepository = userRepository;
        this.invitationTokenRepository = invitationTokenRepository;
        this.candidateRepository = candidateRepository;
        this.vacancyService = vacancyService;
        this.hiringTeamService = hiringTeamService;
        this.pipelineTemplateService = pipelineTemplateService;
        this.applicationService = applicationService;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    /**
     * Seeds everything in one transaction, then spreads the dates over the
     * past weeks so the board and dashboards look lived in.
     *
     * @return {@code false} when the demo data was already there
     */
    public boolean seedIfEmpty() {
        if (tenantService.findBySubdomain(MARKER_SUBDOMAIN).isPresent()) {
            log.info("Demo data already present (company '{}'); nothing seeded.", MARKER_SUBDOMAIN);
            return false;
        }
        posted.clear();
        applied.clear();
        transactions.executeWithoutResult(status -> seed());
        transactions.executeWithoutResult(status -> backdate(Instant.now()));
        log.info("Demo data seeded: 3 companies, {} vacancies, {} applications. The logins are listed "
                + "in docs/DEMO.md.", posted.size(), applied.size());
        return true;
    }

    // ================================================================== seed

    private void seed() {
        passwordHash = passwordEncoder.encode(demoPassword);
        today = LocalDate.now(COLOMBO);

        Candidates candidates = candidates();
        seedLumora(candidates);
        seedSerendib(candidates);
        seedHarbourline(candidates);
    }

    // ---------------------------------------------------------------- Lumora

    private void seedLumora(Candidates c) {
        Company lumora = company("Lumora Technologies", MARKER_SUBDOMAIN,
                "Dilini", "Wickramasinghe", "dilini.wickramasinghe@lumora.test");
        tenantService.updateProfile(lumora.tenantId, new UpdateCompanyProfileRequest(
                "Lumora Technologies",
                "Engineering the software that runs Sri Lankan business",
                "Information Technology", "Private limited company", "201–500 employees", 2014,
                "Lumora Technologies builds cloud platforms for banks, insurers and retailers across "
                        + "South Asia. From our engineering centre in Colombo, 280 engineers, designers and "
                        + "product people ship software used by more than two million customers every day. "
                        + "We work in small, autonomous teams, release several times a week and invest "
                        + "heavily in the people who build our products.",
                "To give every business in the region software as dependable as the people who run it.",
                "A South Asia where world-class products are designed and built at home.",
                "Lumora Technologies (Private) Limited", "PV 104582",
                "Asia/Colombo", LKR, "English",
                "hello@lumora.test", "careers@lumora.test", "+94 11 245 7800", "+94 77 245 7800",
                "https://lumora.test", null, null, null, null,
                COLOMBO_CITY, "Western Province", "00300", SRI_LANKA,
                List.of("REMOTE_HYBRID", "FLEXIBLE_HOURS", "HEALTH_INSURANCE", "TRAINING",
                        "PERFORMANCE_BONUS", "CAREER_DEVELOPMENT"),
                List.of("Colombo 03 (headquarters)", "Kandy"),
                List.of("Engineering", "Platform", "Quality Engineering", "Design", "Data", "Product"),
                List.of("Finance & Banking")));
        tenantService.updateSettings(lumora.tenantId, new UpdateTenantSettingsRequest(false, true, true));

        UUID kasun = member(lumora, SystemRole.RECRUITER, "Kasun", "Perera", "kasun.perera@lumora.test");
        UUID arjun = member(lumora, SystemRole.INTERVIEWER, "Arjun", "Selvaraj", "arjun.selvaraj@lumora.test");
        UUID nimesha = member(lumora, SystemRole.INTERVIEWER, "Nimesha", "Jayasuriya",
                "nimesha.jayasuriya@lumora.test");
        // Joined as an interviewer, then promoted: leaves a role change in the audit trail.
        UUID shanaya = member(lumora, SystemRole.INTERVIEWER, "Shanaya", "Fernando",
                "shanaya.fernando@lumora.test");
        tenantRoleService.changeMemberRole(lumora.tenantId, lumora.ownerId, shanaya,
                lumora.roles.get(SystemRole.RECRUITER).getId());

        TenantRole coordinator = customRole(lumora, "Hiring Coordinator",
                "Runs the interview calendar and keeps the hiring team in step.",
                Permission.TEAM_VIEW, Permission.TEAM_INVITE, Permission.APPLICATIONS_VIEW_ALL,
                Permission.INTERVIEWS_SCHEDULE);
        TenantRole junior = customRole(lumora, "Junior Recruiter",
                "Drafts and runs their own vacancies; sees only the ones they work on.",
                Permission.JOBS_MANAGE, Permission.APPLICATIONS_MANAGE);
        member(lumora, coordinator, "Fathima", "Rizvi", "fathima.rizvi@lumora.test");
        UUID tharindu = member(lumora, junior, "Tharindu", "Bandara", "tharindu.bandara@lumora.test");
        invited(lumora, SystemRole.INTERVIEWER, "Ishara", "Gunawardena", "ishara.gunawardena@lumora.test");

        template(lumora, "Engineering hiring",
                "Technical screen, a pairing interview and a conversation about how we work.", 75, 50, true,
                stage(StageType.POST_APPLICATION, "Application received", null),
                stage(StageType.SCREENING, "Recruiter screen", null),
                stage(StageType.INTERVIEW, "Technical interview", 70),
                stage(StageType.INTERVIEW, "Team and values interview", 60),
                stage(StageType.ONBOARD, "Offer and onboarding", null));
        template(lumora, "Graduate and internship programme",
                "For interns and graduate engineers: an aptitude test and one panel interview.", 70, 45, false,
                stage(StageType.POST_APPLICATION, "Application received", null),
                stage(StageType.SCREENING, "Aptitude test", null),
                stage(StageType.INTERVIEW, "Panel interview", 60),
                stage(StageType.ONBOARD, "Offer and onboarding", null));

        List<String> perks = List.of("REMOTE_HYBRID", "FLEXIBLE_HOURS", "HEALTH_INSURANCE", "TRAINING",
                "PERFORMANCE_BONUS");

        Posted backend = post(lumora, kasun, DemoVacancy.live("Senior Backend Engineer", 9)
                .in("Engineering", "Colombo 03, Sri Lanka").openings(2)
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(28)
                .about("Design and run the Java services behind our core banking platform, used by "
                                + "1.2 million customers a day.",
                        "You will join the Payments squad, one of six product teams building our cloud "
                                + "banking platform. The squad owns the services that move money for retail "
                                + "and business customers, and runs them in production.\n\n"
                                + "We are looking for someone who enjoys turning a hard problem into a simple "
                                + "design, reviews code generously and wants to grow the engineers around "
                                + "them.\n\nYou will work two days a week from our Colombo 03 office.")
                .responsibilities(
                        "Design, build and operate Spring Boot services on Kubernetes",
                        "Lead technical design for new payment features",
                        "Review code and mentor two to three engineers",
                        "Take part in a fair on-call rotation for the services you own")
                .skills(List.of("Java", "Spring Boot", "PostgreSQL", "REST APIs", "Microservices"),
                        List.of("Kafka", "Kubernetes", "AWS"))
                .experience(5, DEGREE_IT)
                .pays(LKR, 450_000, 650_000L, PayPeriod.MONTHLY).benefits(perks).pipeline("TECHNICAL")
                .screening(RIGHT_TO_WORK, NOTICE, "Which Java version do you use in production today?")
                .switches(true, true)
                .recruitedBy(kasun, arjun).team(nimesha));

        Posted frontend = post(lumora, kasun, DemoVacancy.live("Frontend Engineer (React)", 6)
                .in("Engineering", "Remote, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.REMOTE).closesInDays(24)
                .about("Build the customer-facing web apps of our retail and banking products in React "
                                + "and TypeScript.",
                        "Our web apps are how most customers meet Lumora. You will work with designers "
                                + "and backend engineers to ship accessible, fast interfaces, and help shape "
                                + "the shared component library every team uses.\n\nThis role is fully "
                                + "remote within Sri Lanka, with a team day in Colombo once a month.")
                .responsibilities(
                        "Build features in React and TypeScript from design to production",
                        "Contribute to and maintain our shared design system",
                        "Keep pages fast and accessible (WCAG 2.1 AA)",
                        "Write unit and end-to-end tests")
                .skills(List.of("React", "TypeScript", "CSS", "Testing Library"),
                        List.of("Next.js", "Storybook", "Playwright"))
                .experience(3, DEGREE_IT)
                .pays(LKR, 300_000, 450_000L, PayPeriod.MONTHLY).benefits(perks).pipeline("TECHNICAL")
                .screening(RIGHT_TO_WORK, "Link to a project you are proud of")
                .switches(true, false)
                .recruitedBy(shanaya, arjun).team(nimesha));

        Posted devops = post(lumora, kasun, DemoVacancy.live("DevOps Engineer", 12)
                .in("Platform", "Colombo 03, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(20)
                .about("Run the AWS and Kubernetes platform that every Lumora team deploys to.",
                        "The Platform team gives product teams a paved road to production: CI/CD, "
                                + "observability, infrastructure as code and a reliable Kubernetes estate. "
                                + "You will automate the boring parts and make the hard parts safe.")
                .responsibilities(
                        "Operate and improve our EKS clusters and CI/CD pipelines",
                        "Manage infrastructure with Terraform",
                        "Build dashboards and alerts that teams actually use",
                        "Lead blameless post-incident reviews")
                .skills(List.of("AWS", "Kubernetes", "Terraform", "CI/CD", "Linux"),
                        List.of("Prometheus", "Grafana", "Argo CD"))
                .experience(4, DEGREE_IT).certifications("AWS Certified SysOps Administrator (preferred)")
                .pays(LKR, 400_000, 550_000L, PayPeriod.MONTHLY).benefits(perks).pipeline("TECHNICAL")
                .screening(RIGHT_TO_WORK, NOTICE)
                .recruitedBy(kasun, arjun));

        Posted qa = post(lumora, shanaya, DemoVacancy.live("QA Automation Engineer", 4)
                .in("Quality Engineering", "Colombo 03, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(26)
                .about("Automate the tests that let our teams release with confidence several times a week.",
                        "You will build and maintain API and UI test suites, work with developers on "
                                + "testability, and keep our release pipeline green.")
                .responsibilities(
                        "Write automated API and UI tests",
                        "Own the regression suite for two product areas",
                        "Work with developers to design testable features")
                .skills(List.of("Selenium", "Java", "REST Assured", "Test design"),
                        List.of("Cypress", "Performance testing"))
                .experience(2, "Degree in Computing, Software Engineering or equivalent")
                .certifications("ISTQB Foundation Level (preferred)")
                .pays(LKR, 220_000, 320_000L, PayPeriod.MONTHLY).benefits(perks)
                .screening(RIGHT_TO_WORK)
                .switches(false, true)
                .recruitedBy(shanaya, nimesha));

        Posted designer = post(lumora, shanaya, DemoVacancy.live("Product Designer (UI/UX)", 15)
                .in("Design", "Remote, Sri Lanka")
                .as(EmploymentType.CONTRACT, WorkplaceType.REMOTE).closesInDays(14)
                .about("Twelve-month contract to redesign our mobile banking experience end to end.",
                        "You will lead research, flows and high-fidelity design for the next version of "
                                + "our mobile banking app, working with a product manager and four engineers.")
                .responsibilities(
                        "Run user research and usability tests",
                        "Design flows, prototypes and final UI in Figma",
                        "Contribute components to the design system")
                .skills(List.of("Figma", "User research", "Prototyping", "Interaction design"),
                        List.of("Design systems", "Accessibility"))
                .experience(3, "Degree or diploma in Design, HCI or a related field")
                .otherRequirements("Please include a portfolio link in your application.")
                .pays(LKR, 280_000, 380_000L, PayPeriod.MONTHLY).benefits(List.of("FLEXIBLE_HOURS", "REMOTE_HYBRID"))
                .screening("Portfolio link")
                .recruitedBy(shanaya, null));

        Posted intern = post(lumora, kasun, DemoVacancy.live("Software Engineering Intern", 3)
                .in("Engineering", "Colombo 03, Sri Lanka").openings(6)
                .as(EmploymentType.INTERNSHIP, WorkplaceType.ON_SITE).closesInDays(35)
                .about("A six-month paid internship on a real product team, with a mentor from day one.",
                        "Interns at Lumora ship code to production. You will join a product team, pair with "
                                + "a mentor and finish with a project you can show. Many of our engineers "
                                + "started here.")
                .responsibilities(
                        "Build and test features with your team",
                        "Take part in code reviews and stand-ups",
                        "Present your project at the end of the internship")
                .skills(List.of("Java or JavaScript", "Data structures", "Git"), List.of("Spring Boot", "React"))
                .experience(0, "Undergraduate in Computer Science or Software Engineering")
                .pays(LKR, 60_000, 80_000L, PayPeriod.MONTHLY).benefits(List.of("TRAINING", "MEALS"))
                .screening("When can you start, and for how long?")
                .recruitedBy(kasun, nimesha));

        Posted analyst = post(lumora, kasun, DemoVacancy.live("Data Analyst – Fintech", 7)
                .in("Data", "Colombo 03, Sri Lanka").industry("Finance & Banking")
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(21)
                .about("Turn transaction data into insight for our banking clients' product and risk teams.",
                        "You will build the reports and models our banking clients use to understand "
                                + "customer behaviour and spot risk early.")
                .responsibilities(
                        "Build dashboards and recurring reports",
                        "Analyse transaction trends with SQL and Python",
                        "Present findings to client stakeholders")
                .skills(List.of("SQL", "Python", "Power BI", "Statistics"), List.of("dbt", "Banking domain"))
                .experience(2, "Degree in Statistics, Mathematics, Economics or Computing")
                .pays(LKR, 250_000, 350_000L, PayPeriod.MONTHLY).benefits(perks)
                .screening(RIGHT_TO_WORK)
                .recruitedBy(kasun, null));

        post(lumora, lumora.ownerId, DemoVacancy.draft("Engineering Manager", 2)
                .in("Engineering", "Colombo 03, Sri Lanka")
                .about("Lead two product squads and grow their engineers.", "")
                .recruitedBy(kasun, null));
        // Filed by the junior recruiter: the one vacancy they can see.
        post(lumora, tharindu, DemoVacancy.draft("Business Analyst", 1)
                .in("Product", "Colombo 03, Sri Lanka")
                .about("Translate client needs into clear requirements for our product teams.", ""));

        Posted writer = post(lumora, shanaya, DemoVacancy.closed("Technical Writer", 34)
                .in("Product", "Remote, Sri Lanka")
                .as(EmploymentType.PART_TIME, WorkplaceType.REMOTE).closesInDays(5)
                .about("Write the developer documentation for our banking APIs.",
                        "Part-time role writing guides, API references and release notes.")
                .responsibilities("Write and maintain API documentation", "Edit release notes")
                .skills(List.of("Technical writing", "Markdown", "REST APIs"), List.of("OpenAPI"))
                .experience(2, "Degree in English, Communication or Computing")
                .pays(LKR, 1_500, 2_200L, PayPeriod.HOURLY)
                .recruitedBy(shanaya, null));
        post(lumora, kasun, DemoVacancy.archived("Mobile Developer (Flutter)", 70)
                .in("Engineering", "Colombo 03, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(5)
                .about("Build our cross-platform mobile apps in Flutter.",
                        "Filled in August — kept for reporting.")
                .responsibilities("Build features in Flutter", "Publish to the App Store and Google Play")
                .skills(List.of("Flutter", "Dart"), List.of("Firebase"))
                .experience(3, DEGREE_IT)
                .pays(LKR, 300_000, 420_000L, PayPeriod.MONTHLY)
                .recruitedBy(kasun, arjun));

        apply(c.ashan, backend, ApplicationStatus.SHORTLISTED, 1);
        apply(c.aadhil, backend, ApplicationStatus.IN_REVIEW, 2);
        apply(c.hiruni, backend, ApplicationStatus.REJECTED, 3);
        apply(c.kavindya, frontend, ApplicationStatus.IN_REVIEW, 1);
        apply(c.tharushi, frontend, ApplicationStatus.APPLIED, 4);
        apply(c.aadhil, devops, ApplicationStatus.SHORTLISTED, 2);
        apply(c.tharushi, qa, ApplicationStatus.SHORTLISTED, 1);
        apply(c.hiruni, qa, ApplicationStatus.APPLIED, 2);
        apply(c.kavindya, designer, ApplicationStatus.SHORTLISTED, 3);
        apply(c.hiruni, intern, ApplicationStatus.IN_REVIEW, 1);
        apply(c.shalini, analyst, ApplicationStatus.SHORTLISTED, 2);
        apply(c.yohan, analyst, ApplicationStatus.IN_REVIEW, 3);
        apply(c.tharushi, writer, ApplicationStatus.REJECTED, 4);

        finishLifecycles(lumora);
    }

    // -------------------------------------------------------------- Serendib

    private void seedSerendib(Candidates c) {
        Company serendib = company("Serendib Health Partners", "serendib-health",
                "Ruwan", "Abeysekera", "ruwan.abeysekera@serendibhealth.test");
        tenantService.updateProfile(serendib.tenantId, new UpdateCompanyProfileRequest(
                "Serendib Health Partners",
                "Compassionate care, clinical excellence",
                "Healthcare", "Public limited company", "500+ employees", 2006,
                "Serendib Health Partners runs three multi-specialty hospitals and a school of nursing in "
                        + "Colombo and Kandy. Our 1,400 clinicians and support staff care for more than "
                        + "250,000 patients a year, and we train the next generation of nurses on site.",
                "To give every patient the care we would want for our own families.",
                "To be the hospital group Sri Lanka trusts most, for care and for careers.",
                "Serendib Health Partners PLC", "PQ 2291",
                "Asia/Colombo", LKR, "English",
                "hello@serendibhealth.test", "careers@serendibhealth.test", "+94 11 268 9900", null,
                "https://serendibhealth.test", null, null, null, null,
                COLOMBO_CITY, "Western Province", "00500", SRI_LANKA,
                List.of("HEALTH_INSURANCE", "PAID_LEAVE", "PARENTAL_LEAVE", "TRAINING", "MEALS", "TRANSPORT"),
                List.of("Colombo 05", "Kandy"),
                List.of("Nursing", "Medical", "Pharmacy", "Clinical Education", "Patient Services", "Laboratory"),
                List.of("Education")));
        // Reviewers see applications without names until a shortlist.
        tenantService.updateSettings(serendib.tenantId, new UpdateTenantSettingsRequest(true, false, true));

        UUID priyanka = member(serendib, SystemRole.RECRUITER, "Priyanka", "Nadarajah",
                "priyanka.nadarajah@serendibhealth.test");
        UUID hashan = member(serendib, SystemRole.INTERVIEWER, "Hashan", "Weerasekara",
                "hashan.weerasekara@serendibhealth.test");
        TenantRole panel = customRole(serendib, "Clinical Panel",
                "Senior clinicians who review applicants and sit on interview panels.",
                Permission.APPLICATIONS_VIEW_ALL, Permission.INTERVIEWS_CONDUCT);
        UUID malini = member(serendib, panel, "Malini", "Rodrigo", "malini.rodrigo@serendibhealth.test");
        UUID sanduni = member(serendib, SystemRole.RECRUITER, "Sanduni", "Karunaratne", CONSULTANT_EMAIL);

        template(serendib, "Clinical hiring",
                "Credential check, a clinical panel and a practical assessment on the ward.", 80, 55, false,
                stage(StageType.POST_APPLICATION, "Application received", null),
                stage(StageType.SCREENING, "Licence and credential check", null),
                stage(StageType.INTERVIEW, "Clinical panel", 70),
                stage(StageType.INTERVIEW, "Practical assessment", 75),
                stage(StageType.ONBOARD, "Offer and induction", null));

        List<String> care = List.of("HEALTH_INSURANCE", "PAID_LEAVE", "PARENTAL_LEAVE", "TRAINING", "MEALS");
        List<WeekDay> rota = List.of(WeekDay.MON, WeekDay.TUE, WeekDay.WED, WeekDay.THU, WeekDay.FRI,
                WeekDay.SAT, WeekDay.SUN);

        Posted nurse = post(serendib, priyanka, DemoVacancy.live("Registered Nurse – Intensive Care", 5)
                .in("Nursing", "Colombo 05, Sri Lanka").openings(4)
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(25)
                .about("Care for critically ill patients in our 18-bed adult intensive care unit.",
                        "You will join a team of 60 ICU nurses working alongside intensivists, "
                                + "physiotherapists and pharmacists. We offer a structured critical-care "
                                + "induction and funded post-basic training.")
                .responsibilities(
                        "Deliver and document nursing care for ventilated patients",
                        "Monitor patients and respond to deterioration",
                        "Support families and educate patients on discharge")
                .skills(List.of("Critical care nursing", "Patient monitoring", "Basic Life Support"),
                        List.of("Advanced Cardiac Life Support", "Ventilator management"))
                .experience(2, "Diploma or BSc in Nursing")
                .certifications("Sri Lanka Nursing Council registration", "Basic Life Support")
                .languages("English", "Sinhala", "Tamil")
                .pays(LKR, 150_000, 220_000L, PayPeriod.MONTHLY).benefits(care)
                .schedule(rota, "12-hour rotating shifts", ShiftType.ROTATING, 48).pipeline("STANDARD")
                .screening("Are you registered with the Sri Lanka Nursing Council?", NOTICE)
                .recruitedBy(priyanka, hashan).team(malini));

        Posted cardiologist = post(serendib, priyanka, DemoVacancy.live("Consultant Cardiologist", 11)
                .in("Medical", "Colombo 05, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(40)
                .about("Lead the cardiology service at our flagship Colombo hospital.",
                        "You will lead a team of three cardiologists and a cath lab, and help us expand "
                                + "our heart failure clinic.")
                .responsibilities(
                        "Run outpatient clinics and ward rounds",
                        "Perform and supervise interventional procedures",
                        "Teach registrars and nursing staff")
                .skills(List.of("Interventional cardiology", "Echocardiography", "Clinical leadership"),
                        List.of("Heart failure management"))
                .experience(8, "MD (Medicine) with board certification in Cardiology")
                .certifications("Sri Lanka Medical Council registration")
                .pipeline("EXECUTIVE")
                .screening("Are you registered with the Sri Lanka Medical Council?")
                .recruitedBy(priyanka, null).team(hashan));

        Posted pharmacist = post(serendib, sanduni, DemoVacancy.live("Pharmacist", 8)
                .in("Pharmacy", "Kandy, Sri Lanka")
                .as(EmploymentType.PART_TIME, WorkplaceType.ON_SITE).closesInDays(18)
                .about("Run the outpatient pharmacy at our Kandy hospital, four days a week.",
                        "You will dispense prescriptions, counsel patients and keep stock safe and "
                                + "compliant.")
                .responsibilities("Dispense and check prescriptions", "Counsel patients on their medicines",
                        "Manage controlled-drug records")
                .skills(List.of("Dispensing", "Patient counselling", "Inventory management"), List.of())
                .experience(1, "BPharm or equivalent")
                .certifications("Registration with the Sri Lanka Medical Council (Pharmacy)")
                .pays(LKR, 120_000, 160_000L, PayPeriod.MONTHLY).benefits(care)
                .schedule(List.of(WeekDay.MON, WeekDay.TUE, WeekDay.THU, WeekDay.SAT), "8:00 AM – 4:00 PM",
                        ShiftType.DAY, 32)
                .recruitedBy(sanduni, null));

        Posted educator = post(serendib, priyanka, DemoVacancy.live("Clinical Training Coordinator", 13)
                .in("Clinical Education", "Colombo 05, Sri Lanka").industry("Education")
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(16)
                .about("Plan and run the training programme of our school of nursing.",
                        "You will design the timetable, coordinate clinical placements and track student "
                                + "progress for 120 nursing students a year.")
                .responsibilities("Plan the training calendar", "Coordinate ward placements",
                        "Report on student progress")
                .skills(List.of("Curriculum planning", "Nursing education", "Stakeholder management"),
                        List.of("Learning management systems"))
                .experience(4, "BSc in Nursing; a teaching qualification is an advantage")
                .pays(LKR, 180_000, 240_000L, PayPeriod.MONTHLY).benefits(care)
                .recruitedBy(priyanka, null));

        post(serendib, priyanka, DemoVacancy.draft("Medical Laboratory Technologist", 1)
                .in("Laboratory", "Colombo 05, Sri Lanka")
                .about("Run haematology and biochemistry tests in our accredited laboratory.", ""));
        Posted frontOffice = post(serendib, priyanka, DemoVacancy.closed("Front Office Executive", 29)
                .in("Patient Services", "Colombo 05, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(5)
                .about("Welcome patients and manage admissions at our Colombo hospital.",
                        "Front-desk role handling admissions, billing questions and appointments.")
                .responsibilities("Admit and register patients", "Handle appointment bookings")
                .skills(List.of("Customer service", "Hospital information systems"), List.of())
                .experience(1, "GCE A/L or diploma in Hospitality or Business")
                .pays(LKR, 85_000, 110_000L, PayPeriod.MONTHLY)
                .recruitedBy(priyanka, null));

        apply(c.nuwan, nurse, ApplicationStatus.SHORTLISTED, 1);
        apply(c.sachini, nurse, ApplicationStatus.REJECTED, 2);
        apply(c.sachini, pharmacist, ApplicationStatus.IN_REVIEW, 1);
        apply(c.nuwan, educator, ApplicationStatus.APPLIED, 3);
        apply(c.consultant, cardiologist, ApplicationStatus.IN_REVIEW, 2);
        apply(c.yohan, frontOffice, ApplicationStatus.REJECTED, 2);

        finishLifecycles(serendib);
    }

    // ----------------------------------------------------------- Harbourline

    private void seedHarbourline(Candidates c) {
        Company harbourline = company("Harbourline Logistics", "harbourline",
                "Imran", "Marikar", "imran.marikar@harbourline.test");
        tenantService.updateProfile(harbourline.tenantId, new UpdateCompanyProfileRequest(
                "Harbourline Logistics",
                "Moving Sri Lanka's trade, on time",
                "Logistics & Transport", "Private limited company", "201–500 employees", 2011,
                "Harbourline Logistics handles freight forwarding, warehousing and last-mile delivery for "
                        + "exporters and online retailers. We operate from the Port of Colombo, the "
                        + "Katunayake export processing zone and six regional depots, with a fleet of 140 "
                        + "vehicles.",
                "To make shipping as simple and predictable for a small exporter as for a multinational.",
                "The most reliable link between Sri Lankan businesses and their customers.",
                "Harbourline Logistics (Private) Limited", "PV 87310",
                "Asia/Colombo", LKR, "English",
                "hello@harbourline.test", "careers@harbourline.test", "+94 11 252 3400", null,
                "https://harbourline.test", null, null, null, null,
                COLOMBO_CITY, "Western Province", "01500", SRI_LANKA,
                List.of("TRANSPORT", "MEALS", "HEALTH_INSURANCE", "PERFORMANCE_BONUS", "PAID_LEAVE"),
                List.of("Colombo 15 (headquarters)", "Katunayake", "Kurunegala"),
                List.of("Operations", "Fleet", "Fulfilment", "Customs & Compliance", "Warehousing"),
                List.of("Retail & E-commerce")));

        UUID chamari = member(harbourline, SystemRole.RECRUITER, "Chamari", "Dissanayake",
                "chamari.dissanayake@harbourline.test");
        UUID roshan = member(harbourline, SystemRole.INTERVIEWER, "Roshan", "de Silva",
                "roshan.desilva@harbourline.test");
        UUID sanduni = member(harbourline, SystemRole.INTERVIEWER, "Sanduni", "Karunaratne", CONSULTANT_EMAIL);

        template(harbourline, "Operations hiring",
                "A phone screen and one interview with the operations lead.", 70, 50, false,
                stage(StageType.POST_APPLICATION, "Application received", null),
                stage(StageType.SCREENING, "Phone screen", null),
                stage(StageType.INTERVIEW, "Operations interview", 60),
                stage(StageType.ONBOARD, "Offer and induction", null));

        List<String> perks = List.of("TRANSPORT", "MEALS", "HEALTH_INSURANCE", "PERFORMANCE_BONUS");
        List<WeekDay> sixDays = List.of(WeekDay.MON, WeekDay.TUE, WeekDay.WED, WeekDay.THU, WeekDay.FRI,
                WeekDay.SAT);

        Posted coordinator = post(harbourline, chamari, DemoVacancy.live("Logistics Coordinator", 2)
                .in("Operations", "Colombo 15, Sri Lanka").openings(3)
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(22)
                .about("Plan and track export shipments from booking to delivery.",
                        "You will coordinate bookings with shipping lines, prepare documents and keep "
                                + "customers updated until their cargo arrives.")
                .responsibilities("Book and track shipments", "Prepare shipping documents",
                        "Update customers on cargo status")
                .skills(List.of("Freight forwarding", "MS Excel", "Customer communication"),
                        List.of("CargoWise"))
                .experience(2, "Diploma or degree in Logistics or Business")
                .languages("English", "Sinhala")
                .pays(LKR, 120_000, 180_000L, PayPeriod.MONTHLY).benefits(perks)
                .schedule(sixDays, "8:00 AM – 5:00 PM", ShiftType.DAY, 48).pipeline("STANDARD")
                .screening(RIGHT_TO_WORK, NOTICE)
                .recruitedBy(chamari, roshan).team(sanduni));

        Posted fleet = post(harbourline, chamari, DemoVacancy.live("Fleet Operations Manager", 10)
                .in("Fleet", "Colombo 15, Sri Lanka")
                .as(EmploymentType.FULL_TIME, WorkplaceType.ON_SITE).closesInDays(30)
                .about("Run our 140-vehicle fleet: routing, maintenance, safety and drivers.",
                        "You will own fleet performance across six depots and lead a team of five "
                                + "supervisors.")
                .responsibilities("Plan routes and vehicle utilisation", "Manage maintenance schedules",
                        "Lead depot supervisors and driver safety")
                .skills(List.of("Fleet management", "Route planning", "People management"),
                        List.of("Telematics"))
                .experience(6, "Degree in Logistics, Engineering or Management")
                .pays(LKR, 350_000, 480_000L, PayPeriod.MONTHLY).benefits(perks)
                .pipeline("EXECUTIVE")
                .recruitedBy(chamari, roshan));

        Posted fulfilment = post(harbourline, chamari, DemoVacancy.live("E-commerce Fulfilment Lead", 6)
                .in("Fulfilment", "Katunayake, Sri Lanka").industry("Retail & E-commerce")
                .as(EmploymentType.FULL_TIME, WorkplaceType.HYBRID).closesInDays(19)
                .about("Run same-day order fulfilment for our online-retail clients.",
                        "You will lead the pick-pack-ship team at our Katunayake fulfilment centre and "
                                + "keep same-day dispatch above 98%.")
                .responsibilities("Lead the pick, pack and dispatch team", "Track and improve dispatch times",
                        "Work with retail clients on peak-season plans")
                .skills(List.of("Warehouse operations", "Team leadership", "WMS"), List.of("Lean"))
                .experience(3, "Degree or diploma in Logistics or Supply Chain")
                .pays(LKR, 200_000, 280_000L, PayPeriod.MONTHLY).benefits(perks)
                .recruitedBy(chamari, null));

        Posted customs = post(harbourline, chamari, DemoVacancy.live("Customs Documentation Officer", 1)
                .in("Customs & Compliance", "Colombo 01, Sri Lanka").openings(2)
                .as(EmploymentType.TEMPORARY, WorkplaceType.ON_SITE).closesInDays(12)
                .about("Six-month temporary role preparing customs declarations for the year-end peak.",
                        "You will prepare and lodge declarations with Sri Lanka Customs and resolve queries.")
                .responsibilities("Prepare customs declarations", "Resolve documentation queries")
                .skills(List.of("Customs procedures", "ASYCUDA", "Attention to detail"), List.of())
                .experience(1, "GCE A/L; a customs or shipping diploma is an advantage")
                .pays(LKR, 1_200, 1_500L, PayPeriod.HOURLY)
                .recruitedBy(chamari, null));

        post(harbourline, chamari, DemoVacancy.draft("Warehouse Supervisor", 1)
                .in("Warehousing", "Kurunegala, Sri Lanka")
                .about("Supervise inbound and outbound operations at our Kurunegala depot.", ""));

        apply(c.dinesh, coordinator, ApplicationStatus.SHORTLISTED, 1);
        apply(c.shalini, coordinator, ApplicationStatus.APPLIED, 1);
        apply(c.dinesh, fulfilment, ApplicationStatus.IN_REVIEW, 2);
        apply(c.yohan, customs, ApplicationStatus.APPLIED, 0);
        apply(c.ashan, fleet, ApplicationStatus.REJECTED, 3);

        finishLifecycles(harbourline);
    }

    // ------------------------------------------------------------ candidates

    private Candidates candidates() {
        return new Candidates(
                candidate("Ashan Gunasekara", "ashan.gunasekara@mail.test", "199214503217",
                        "27 Flower Road, Colombo 07", "+94 77 312 4587", true),
                candidate("Tharushi Perera", "tharushi.perera@mail.test", "975621458V",
                        "14/3 Temple Lane, Nugegoda", "+94 71 455 2390", true),
                candidate("Mohamed Aadhil", "mohamed.aadhil@mail.test", "199506702934",
                        "88 Marine Drive, Colombo 06", "+94 76 220 9184", false),
                candidate("Kavindya Silva", "kavindya.silva@mail.test", "199872301856",
                        "5 Lake Crescent, Kandy", "+94 70 318 7752", true),
                candidate("Shalini Ramanathan", "shalini.ramanathan@mail.test", "966051247V",
                        "102 Station Road, Wellawatte, Colombo 06", "+94 77 640 1123", true),
                candidate("Nuwan Jayawardena", "nuwan.jayawardena@mail.test", "912340876V",
                        "61 Hospital Road, Kalubowila", "+94 72 509 6631", false),
                candidate("Sachini Dias", "sachini.dias@mail.test", "199981204563",
                        "9 Peradeniya Road, Kandy", "+94 75 228 4410", false),
                candidate("Dinesh Kumar Rajendran", "dinesh.rajendran@mail.test", "198831004571",
                        "34 Negombo Road, Wattala", "+94 77 873 2245", true),
                candidate("Hiruni Wijesinghe", "hiruni.wijesinghe@mail.test", "200265409812",
                        "21 University Avenue, Moratuwa", "+94 71 990 3418", true),
                candidate("Yohan Fernando", "yohan.fernando@mail.test", "199019807345",
                        "7 Church Street, Negombo", "+94 76 412 8853", false),
                candidate("Sanduni Karunaratne", CONSULTANT_EMAIL, "199357803219",
                        "16 Park Road, Colombo 05", "+94 77 155 9032", false));
    }

    // =============================================================== helpers

    /** A company with its default roles and its Owner, as registration creates them. */
    private Company company(String name, String subdomain, String ownerFirst, String ownerLast, String ownerEmail) {
        UUID tenantId = tenantService.createTenant(name, subdomain).id();
        Map<SystemRole, TenantRole> roles = tenantRoleService.provisionDefaultRoles(tenantId);
        Company company = new Company(tenantId, roles, null);
        UUID ownerId = member(company, roles.get(SystemRole.OWNER), ownerFirst, ownerLast, ownerEmail);
        return new Company(tenantId, roles, ownerId);
    }

    private UUID member(Company company, SystemRole role, String first, String last, String email) {
        return member(company, company.roles.get(role), first, last, email);
    }

    private UUID member(Company company, TenantRole role, String first, String last, String email) {
        return userRepository.save(new User(company.tenantId, role, tenantRoleService.legacyRoleFor(role),
                email, passwordHash, first, last, UserStatus.ACTIVE)).getId();
    }

    /** Invited but not yet accepted: shows as pending on the team screen. */
    private void invited(Company company, SystemRole role, String first, String last, String email) {
        TenantRole tenantRole = company.roles.get(role);
        User invitee = userRepository.save(new User(company.tenantId, tenantRole,
                tenantRoleService.legacyRoleFor(tenantRole), email, "!invited", first, last, UserStatus.INVITED));
        invitationTokenRepository.save(new InvitationToken(invitee.getId(),
                SecureTokens.sha256(SecureTokens.generate()), Instant.now().plus(Duration.ofDays(6))));
    }

    /** Created by the Owner through the role service, so it appears in the audit trail. */
    private TenantRole customRole(Company company, String name, String description, Permission... permissions) {
        List<String> keys = Arrays.stream(permissions).map(Permission::key).toList();
        UUID roleId = tenantRoleService.createRole(company.tenantId, company.ownerId,
                new RoleRequest(name, description, keys, null)).id();
        return tenantRoleRepository.findByIdAndTenantId(roleId, company.tenantId).orElseThrow();
    }

    private void template(Company company, String name, String description, int auto, int manual,
                          boolean idealMatching, PipelineStageRequest... stages) {
        List<PipelineStageRequest> positioned = new ArrayList<>();
        for (int i = 0; i < stages.length; i++) {
            positioned.add(new PipelineStageRequest(i, stages[i].stageType(), stages[i].name(), stages[i].benchmark()));
        }
        pipelineTemplateService.create(company.tenantId, company.ownerId,
                new PipelineTemplateRequest(name, description, auto, manual, idealMatching, positioned));
    }

    private static PipelineStageRequest stage(StageType type, String name, Integer benchmark) {
        return new PipelineStageRequest(0, type, name, benchmark);
    }

    private UUID candidate(String fullName, String email, String nic, String address, String phone,
                           boolean inTalentPool) {
        Candidate candidate = new Candidate(email, passwordHash, fullName,
                CandidateNicValidator.normalizeAndValidate(nic), address, phone, UserStatus.ACTIVE);
        candidate.setInTalentPool(inTalentPool);
        return candidateRepository.save(candidate).getId();
    }

    /**
     * Files the vacancy as {@code actor}, staffs it, and publishes it unless it
     * is to stay a draft. Closing and archiving wait until its applications
     * are in ({@link #finishLifecycles}).
     */
    private Posted post(Company company, UUID actor, DemoVacancy advert) {
        VacancyViewer viewer = new VacancyViewer(company.tenantId, actor, true);
        UUID id = vacancyService.create(viewer, advert.toRequest(today)).id();
        if (!advert.team.isEmpty()) {
            hiringTeamService.replace(viewer, id, new HiringTeamRequest(advert.team));
        }
        if (advert.finalStatus != VacancyStatus.DRAFT) {
            vacancyService.publish(viewer, id);
        }
        Posted vacancy = new Posted(id, company.tenantId, viewer, advert.finalStatus, advert.daysAgo);
        posted.add(vacancy);
        return vacancy;
    }

    private void apply(UUID candidateId, Posted vacancy, ApplicationStatus status, int daysAfterPublishing) {
        UUID applicationId = applicationService.submit(candidateId, vacancy.id.toString()).id();
        if (status != ApplicationStatus.APPLIED) {
            applicationService.updateStatus(vacancy.tenantId, applicationId, new UpdateApplicationStatusRequest(status));
        }
        applied.add(new Applied(applicationId, vacancy, daysAfterPublishing));
    }

    private void finishLifecycles(Company company) {
        for (Posted vacancy : posted) {
            if (!vacancy.tenantId.equals(company.tenantId)) {
                continue;
            }
            if (vacancy.finalStatus == VacancyStatus.CLOSED || vacancy.finalStatus == VacancyStatus.ARCHIVED) {
                vacancyService.close(vacancy.viewer, vacancy.id);
            }
            if (vacancy.finalStatus == VacancyStatus.ARCHIVED) {
                vacancyService.archive(vacancy.viewer, vacancy.id);
            }
        }
    }

    /**
     * Everything above happened "now". Spread it out: each vacancy was filed
     * {@code daysAgo} days ago and went live a day later; a closed one closed
     * a week after that; applications arrived in the days after publishing.
     */
    private void backdate(Instant now) {
        for (Posted vacancy : posted) {
            Instant created = now.minus(Duration.ofDays(vacancy.daysAgo)).minus(Duration.ofHours(3));
            Instant published = created.plus(Duration.ofDays(1));
            Instant closed = published.plus(Duration.ofDays(7));
            Instant archived = closed.plus(Duration.ofDays(14));
            jdbc.update("""
                    UPDATE job_vacancies
                    SET created_at = ?, updated_at = ?,
                        published_at = CASE WHEN published_at IS NULL THEN NULL ELSE CAST(? AS timestamptz) END,
                        closed_at = CASE WHEN closed_at IS NULL THEN NULL ELSE CAST(? AS timestamptz) END,
                        archived_at = CASE WHEN archived_at IS NULL THEN NULL ELSE CAST(? AS timestamptz) END
                    WHERE id = ?
                    """,
                    ts(created), ts(earliest(now, vacancy.finalStatus == VacancyStatus.DRAFT ? created : published)),
                    ts(earliest(now, published)), ts(earliest(now, closed)), ts(earliest(now, archived)),
                    vacancy.id);
        }
        for (Applied application : applied) {
            Instant published = now.minus(Duration.ofDays(application.vacancy.daysAgo)).plus(Duration.ofHours(21));
            Instant at = earliest(now, published.plus(Duration.ofDays(application.daysAfterPublishing))
                    .plus(Duration.ofHours(2)));
            jdbc.update("UPDATE applications SET created_at = ?, updated_at = ? WHERE id = ?",
                    ts(at), ts(at), application.id);
        }
    }

    private static Instant earliest(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    private static Timestamp ts(Instant instant) {
        return Timestamp.from(instant);
    }

    // ================================================================= types

    private record Company(UUID tenantId, Map<SystemRole, TenantRole> roles, UUID ownerId) {
    }

    private record Posted(UUID id, UUID tenantId, VacancyViewer viewer, VacancyStatus finalStatus, int daysAgo) {
    }

    private record Applied(UUID id, Posted vacancy, int daysAfterPublishing) {
    }

    private record Candidates(UUID ashan, UUID tharushi, UUID aadhil, UUID kavindya, UUID shalini,
                              UUID nuwan, UUID sachini, UUID dinesh, UUID hiruni, UUID yohan,
                              UUID consultant) {
    }
}
