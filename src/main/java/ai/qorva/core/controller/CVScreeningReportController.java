package ai.qorva.core.controller;

import ai.qorva.core.dto.CVScreeningReportDTO;
import ai.qorva.core.dto.QorvaRequestResponse;
import ai.qorva.core.exception.QorvaException;
import ai.qorva.core.service.CVScreeningReportService;
import ai.qorva.core.service.OpenAIService;
import ai.qorva.core.utils.BuildApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reports")
public class CVScreeningReportController extends AbstractQorvaController<CVScreeningReportDTO> {

    private final OpenAIService openAIService;

    @Autowired
    public CVScreeningReportController(CVScreeningReportService service, OpenAIService openAIService) {
        super(service);
		this.openAIService = openAIService;
	}

    @GetMapping(value = "/generate")
    public ResponseEntity<QorvaRequestResponse> generateReport(@RequestHeader("Accept-Language") String languageCode) throws QorvaException {
        return BuildApiResponse.from(this.openAIService.match(listOfCVs,jobDescription, languageCode));
    }

    protected static final String listOfCVs = """
        ROLE & PERSON\s
           JAVA ANALYST DEVELOPER\s
           Mohamed TOURE\s
           KEY SKILLS SUMMARY\s
           Java 21 / Java 17 / Java 11/ Java 8 / JEE / JPA /\s
           Hibernate / Spring / Spring Boot / Spring Cloud / Spring\s
           Webservice / Spring REST API / Spring Integration / Spring\s
           Security / JMS / Kafka / Angular / React / SonarQube /\s
           Nexus / Oracle / MySQL / Postgres / MongoDB / Maven /\s
           Database Design / Software Design and Implementation /\s
           Prompt Engineening\s
           CONTACT\s
           GSM: +32 489 20 34 99\s
           Professional email: mohamed.toure@nsi.com\s
           Private email: demahom.toure@gmail.com\s
           GitHub: https://github.com/touremoh \s
           AVAILABILITY\s
           Interviews To suit\s
           To start work To define\s
           PERSONAL INFO BIRTHDATE October 11, 1987\s
            ADDRESS 24, Rue de la Promenade, B-6791 Athus, Belgique\s
           \s
           \s
           PROFILES\s
           DEVELOPMENT\s
           - Spring applications\s
           - Java JEE applications\s
           - Web and distributed applications;\s
           ARCHITECTURE\s
           - Description and validation of technical web and\s
           distributed architectures;\s
           FUNCTIONAL AND TECHNICAL\s
           ANALYSIS\s
           - Functional and technical analysis of web and distributed\s
           applications (frontend and backend)\s
           - Intraday Liquity Management\s
           - Payment Processing\s
           - Cash Settlement, Collateral;\s
           - Applicatif Aéroport, Système Schengen\s
           - SWIFT message processing, Files Transfer\s
           CUSTOMER RELATIONSHIP - Customer oriented; Committed to Deliver;\s
            \s
           WORK EXPERIENCE\s
           \s
           COMPANY Parental Leave\s
           FROM / TO July 2024 – December 2024\s
           ACTIVITIES\s
           • Taking care of my son\s
           • Developing side projects in various technologies\s
           • Leaned Prompt Engineering with OpenAI on Coursera\s
           TECHNOLOGIES\s
           Java 21, MongoDB, Artificial Intelligence, React, Prompt\s
           Engineering\s
           \s
           \s
           COMPANY NSI (Banque International à Luxembourg)\s
           FROM / TO July 2021 – June 2024\s
           ACTIVITIES\s
           Project: GL22\s
           • Task 1: Development and support of various\s
           microservices of the Financial Messaging and\s
           Payments platforms of the bank such as ILM,\s
           Temenos File Transfer, Payment, IPA, IMAP, Cards,\s
           Dyna-service, SDD Mandate\s
           • Task 2: Training team developers on the usage of\s
           various applications of the Financial Messaging while\s
           also receiving training from colleagues.\s
           • Key responsibilities: lead developer of applications\s
           like ILM and Temenos Files Transfer. Making sure of\s
           the delivery and stability of those application\s
           • Achievements: Successfully completed GL22 project\s
           after 3 years of development, testing and support.\s
           TECHNOLOGIES\s
           Java 17, Java 11, Java 8, Spring Cloud, Spring Boot, Spring\s
           Data REST, Spring Integration, JPA, JSM, Kafka, Oracle, JUnit,\s
           GIT, Maven, Jenkins, Sonar, Lombok, MapStruct, Dynatrace,\s
           ELK, React\s
           \s
           \s
           COMPANY O2XP (Police Grand-Ducale) – www.o2xp.com\s
           FROM / TO January 2021 – June 30, 2021 (6 months)\s
           ACTIVITIES\s
           Project: Global Search Interpol\s
           \s
           • Migration of a Java SOAP WebService to Spring REST\s
           application\s
           • Analyzing legacy code\s
           • Translation of functional requirement into technical\s
           solution\s
           • Integration testing and unit testing\s
           • Weekly reporting the evolution of the project to the\s
           project manager\s
           \s
           Project: SIS-FRE-CRUD\s
           • Translation of functional requirements into technical\s
           solution\s
           • Development of new functionalities \s
           • Bug fixing\s
           • Integration testing and unit testing\s
           \s
           TECHNOLOGIES\s
           Java 11, Spring Cloud, Spring Boot, Spring WebServices, Rest,\s
           JPA, Oracle, JUnit, GIT, Maven, Jenkins, Sonar, Lombok,\s
           MapStruct, Keycloack\s
           \s
           \s
           COMPANY O2XP (Clearstream) – www.o2xp.com\s
           FROM / TO January 2018 – December 2020 (3years)\s
           ACTIVITIES\s
           Project: NCCIP application (Cash)\s
           - Designing the solution and services\s
           - Translation of the requirements into technical solution\s
           - Developing and deploying on site\s
           - Followed up project on customer’s side\s
           - Production support\s
           - Maintenance and development of new functionalities\s
           - Part of React team and components library development\s
           \s
           Project: Cash Collateral\s
           - Translation of the requirements into technical solution\s
           - Developing and deploying on site\s
           - Followed up project on customer’s side\s
           - Production support\s
           - Maintenance and development of new functionalities\s
           TECHNOLOGIES\s
           Java 8, JEE, JSP, Oracle, WebLogic, PL-SQL, Unix, JUnit,\s
           Continuous Integration, GIT, Maven, Jenkins, Sonar, …\s
           \s
           \s
           \s
           COMPANY Service Provider\s
           FROM / TO 03/ 2017 – 06/2017 (3 months)\s
           ACTIVITIES\s
           Project: Ecommerce store development\s
           - Development of a web application to manage an online\s
           store (shoes);\s
           - Designing the solution and services;\s
           - Translation of the requirements into technical solution;\s
           - Developing and deploying on site;\s
           - Followed up project on customer’s side;\s
           - Production support;\s
           TECHNOLOGIES Java, SpringBoot, JSP, Javascript, SQL, …\s
           \s
           COMPANY BE.WAN\s
           FROM / TO 02/ 2016 – 05/2016 (4 months)\s
           ACTIVITIES Project: Iléo (Intership)\s
           - Analysis and development of a web application that\s
           manages contracts and send alerts;\s
           - Designing the solution and services;\s
           - Translation of the requirements into technical solution;\s
           - Developing and deploying on site;\s
           TECHNOLOGIES PHP Laravel 5, AngularJS, SQL, …\s
           \s
            \s
           SKILLS & EDUCATION\s
           DATE 2017, Haute Ecole Henallux (IESN), Namur, Belgium\s
           DIPLOMA Bachelier en informatique de gestion\s
           \s
           IT SKILLS\s
           - Java, Spring, SpringBoot, html5, css3, JSF, EJB, php, C,\s
           C#, .Net, javascript, Android, WPF, XAML, Cobol, SQL,\s
           Oracle, UML\s
           - Netbeans, Android Studio, Visual Studio, Visual Paradigm,\s
           Microsoft Azure, AWS…\s
           SOFT SKILLS & DOMAINS - Team spirit, eager to work with others;\s
           \s
           LANGUAGES\s
            Read Written Spoken\s
           French Native Native Native\s
           English Good command Good command Good command\s
           Dutch Basics Basics Basics\s
        """;

    protected final String jobDescription = """
        We are currently seeking a talented and motivated Freelance Java Developer to join a dynamic team.
                
        As a Java Developer you will play a crucial role in the development of new IT applications and components.
                
        Responsibilities
                
        Take charge of the end-to-end project architecture, from business case requirements to acceptance testing and successful delivery
                
        Use industry-leading models and methods to MAP out architectures and support the design of innovative systems
                
        Seamlessly fit into the overall organizational framework
                
        Requirements
                
        Proficiency in architecting Java applications and SpringBoot
                
        Strong grasp of design patterns to create robust and scalable solutions
                
        Familiarity with REST APIs
                
        Experience in Web application interface design
                
        """;
}
