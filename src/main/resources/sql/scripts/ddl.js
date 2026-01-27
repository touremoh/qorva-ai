use qorva;

db.DemoRequestors.drop();
db.DemoPartners.drop();
db.Tenants.drop();
db.Users.drop();
db.JobsPosts.drop();
db.CVs.drop();
db.ResumeMatches.drop();
db.InterviewQuestions.drop();
db.StripeEventLogs.drop();
db.Chats.drop();
db.ChatMessages.drop();
db.Clients.drop();
db.ClientReports.drop();

// This must be deleted
db.createCollection("DemoPartners", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "DemoRequestors",
            required: ["tenantId", "email"],
            properties: {
                tenantId: {
                    bsonType: "string",
                    description: "must be a valid objectId and is required"
                    },
                email: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                firstName: {
                    bsonType: "string",
                    description: "must be a valid string"
                    },
                lastName: {
                    bsonType: "string",
                    description: "must be a valid string"
                    },
                organizationName: {
                    bsonType: "string",
                    description: "must be a valid string"
                    },
                organizationSize: {
                    bsonType: "string",
                    description: "must be a valid string (eg: 1-10)"
                    },
                nbApplicationsPerMonth: {
                    bsonType: "number",
                    description: "must be a valid number"
                    },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    }
                }
            }
        }

});

db.createCollection("Tenants", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "Tenants",
            required: ["tenantName", "createdAt"],
            properties: {
                tenantName: {
                    bsonType: "string",
                    description: "Tenant name is required"
                    },
                stripeCustomerId: {
                    bsonType: "string",
                    description: "Stripe Customer ID is optional"
                    },
                createdAt: {
                    bsonType: "date",
                    description: "creation timestamp, required"
                    },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "last updated timestamp, required"
                    },
                subscriptionInfo: {
                    bsonType: "object",
                    properties: {
                        subscriptionPlan: {
                            bsonType: "string",
                            description: "e.g. STARTER"
                            },
                        billingCycle: {
                            bsonType: "string",
                            description: "e.g. MONTHLY"
                            },
                        price: {
                            bsonType: "decimal",
                            description: "monthly or yearly price"
                            },
                        priceId: {
                            bsonType: "string",
                            description: "price ID"
                            },
                        subscriptionStatus: {
                            bsonType: "string",
                            description: "current status"
                            },
                        subscriptionId: {
                            bsonType: "string",
                            description: "payment/platform subscription ID"
                            },
                        subscriptionStartDate: {
                            bsonType: "date",
                            description: "when the subscription began"
                            },
                        subscriptionEndDate: {
                            bsonType: "date",
                            description: "when the subscription ends"
                            },
                        accountManager: {
                            bsonType: "string",
                            description: "optional: name or ID of account manager"
                            }
                        },
                        description: "subscription details for this company"
                    }
                }
            }
        }
    }
);

db.createCollection("Users", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "Users",
            required: ["firstName", "lastName", "email", "encryptedPassword", "userAccountStatus", "createdAt", "lastUpdatedAt", "createdBy", "lastUpdatedBy", "tenantId"],
            properties: {
                firstName: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                lastName: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                email: {
                    bsonType: "string",
                    pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
                    description: "must be a valid email address and is required"
                },
                encryptedPassword: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                userAccountStatus: {
                    bsonType: "string",
                    description: "must be a string representing the status and is required"
                },
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                },
                createdBy: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                lastUpdatedBy: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                permissions: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            role: {
                                bsonType: "string",
                                description: "must be a string and is required"
                            },
                            action: {
                                bsonType: "string",
                                description: "must be a string and is required"
                            },
                            permission: {
                                bsonType: "string",
                                description: "must be a string and is required"
                            }
                        }
                    }
                }
            }
        }
    }
});


db.createCollection("JobsPosts", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "JobsPosts",
            required: ["title", "description", "tenantId", "createdAt", "lastUpdatedAt", "createdBy", "lastUpdatedBy", "status"],
            properties: {
                title: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                description: {
                    bsonType: "string",
                    description: "must be a string and is required"
                },
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                },
                clientId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                },
                createdBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                },
                lastUpdatedBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                },
                status: {
                    bsonType: "string",
                    enum: ["open", "closed"],
                    description: "must be a string and can only be 'open' or 'closed'"
                },
                scoringConfig: {
                    bsonType: "object",
                    properties: {
                        skills: {
                            bsonType: "array",
                            items: {
                                bsonType: "object",
                                properties: {
                                    name: {
                                        bsonType: "string",
                                        description: "must be a string and is required"
                                    },
                                    importance: {
                                        bsonType: "string",
                                        enum: ["mandatory", "important", "nice_to_have"],
                                        description: "must be a valid string and is required"
                                    },
                                    weight: {
                                        bsonType: "decimal",
                                        description: "must be a valid int and is required"
                                    },
                                    minYearsOfExperience: {
                                        bsonType: "int",
                                        description: "must be a valid int and is required"
                                    },
                                    exactSkillOnly: {
                                        bsonType: "boolean",
                                        description: "must be a boolean and is required"
                                    }
                                }
                            },
                            description: "must be an array of objects and is required"
                        }
                    },
                    experienceRequirements: {
                        bsonType: "object",
                        properties: {
                            minYearsOfExperience: {
                                bsonType: "int",
                                description: "must be a valid int and is required"
                            },
                            minRelevantYears: {
                                bsonType: "int",
                                description: "must be a valid int and is required"
                            },
                            seniorityLevel: {
                                bsonType: "string",
                                enum: ["junior", "mid", "senior"],
                                description: "must be a valid string and is required"
                            }
                        }
                    },
                    locationPreferences: {
                        bsonType: "object",
                        properties: {
                            allowedLocations: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string",
                                    description: "must be a string and is required"
                                }
                            },
                            remoteAllowed: {
                                bsonType: "boolean",
                                description: "must be a boolean and is required"
                            },
                            strictness: {
                                bsonType: "string",
                                enum: ["strict", "medium", "relaxed"],
                                description: "must be a valid string and is required"
                            }
                        }
                    },
                    industryPreferences: {
                        bsonType: "object",
                        properties: {
                            preferredIndustries: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string",
                                    description: "must be a string and is required"
                                }
                            },
                            strictness: {
                                bsonType: "string",
                                enum: ["strict", "medium", "relaxed"],
                                description: "must be a valid string and is required"
                            }
                        }
                    },
                    scoringWeight: {
                        bsonType: "object",
                        properties: {
                            skills: {
                                bsonType: "decimal",
                                description: "must be a valid int and is required"
                            },
                            experience: {
                                bsonType: "decimal",
                                description: "must be a valid int and is required"
                            },
                            location: {
                                bsonType: "decimal",
                                description: "must be a valid int and is required"
                            },
                            industry: {
                                bsonType: "decimal",
                                description: "must be a valid int and is required"
                            }
                        }
                    }
                }
            }
        }
    }
});

db.createCollection("CVs", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "CVs",
            required: ["tenantId", "candidateProfileSummary", "nbYearsOfExperience", "personalInformation", "createdAt", "lastUpdatedAt"],
            properties: {
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
               clientId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
                candidateProfileSummary: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                nbYearsOfExperience: {
                    bsonType: "int",
                    description: "must be a valid int and is required"
                    },
                personalInformation: {
                    bsonType: "object",
                    required: ["name"],
                    properties: {
                        name: {
                            bsonType: "string",
                            description: "must be a string and is required"
                            },
                        contact: {
                            bsonType: "object",
                            properties: {
                                phone: {
                                    bsonType: "string",
                                    description: "must be a string and is required"
                                    },
                                email: {
                                    bsonType: "string",
                                    pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
                                    description: "must be a valid email address and is required"
                                    },
                                socialLinks: {
                                    bsonType: "object",
                                    properties: {
                                        linkedin: {
                                            bsonType: "string",
                                            description: "must be a string"
                                            },
                                        github: {
                                            bsonType: "string",
                                            description: "must be a string"
                                            },
                                        website: {
                                            bsonType: "string",
                                            description: "must be a string"
                                            }
                                        }
                                    }
                                }
                            },
                        role: {
                            bsonType: "string",
                            description: "must be a string and is required"
                            },
                        availability: {
                            bsonType: "object",
                            properties: {
                                interviews: {
                                    bsonType: "string",
                                    description: "must be a string and is required"
                                    },
                                startDate: {
                                    bsonType: "string",
                                    description: "must be a string and is required"
                                    }
                                }
                            },
                        summary: {
                            bsonType: "string",
                            description: "must be a string and is required"
                            }
                        }
                    },
                keySkills: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            category: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            skills: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string"
                                    },
                                description: "must be an array of strings and is required"
                                }
                            }
                        }
                    },
                profiles: {
                    bsonType: "object",
                    properties: {
                        areasOfExpertise: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            },
                        keyResponsibilities: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            }
                        }
                    },
                workExperience: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            company: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            website: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            location: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            from: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            to: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            position: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            activities: {
                                bsonType: "array",
                                items: {
                                    bsonType: "object",
                                    properties: {
                                        project: {
                                            bsonType: "string",
                                            description: "must be a string and is required"
                                            },
                                        tasks: {
                                            bsonType: "array",
                                            items: {
                                                bsonType: "string"
                                                },
                                            description: "must be an array of strings and is required"
                                            }
                                        }
                                    }
                                },
                            achievements: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string"
                                    },
                                description: "must be an array of strings"
                                },
                            toolsAndTechnologies: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string"
                                    },
                                description: "must be an array of strings"
                                }
                            }
                        }
                    },
                education: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            year: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            institution: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            degree: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            fieldOfStudy: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            achievements: {
                                bsonType: "array",
                                items: {
                                    bsonType: "string"
                                    },
                                description: "must be an array of strings"
                                }
                            }
                        }
                    },
                certifications: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            title: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            institution: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            year: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            description: {
                                bsonType: "string",
                                description: "must be a string"
                                }
                            }
                        }
                    },
                skillsAndQualifications: {
                    bsonType: "object",
                    properties: {
                        technicalSkills: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            },
                        softSkills: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            },
                        languages: {
                            bsonType: "array",
                            items: {
                                bsonType: "object",
                                properties: {
                                    language: {
                                        bsonType: "string",
                                        description: "must be a string and is required"
                                        },
                                    proficiency: {
                                        bsonType: "object",
                                        properties: {
                                            read: {
                                                bsonType: "string",
                                                description: "must be a string and is required"
                                                },
                                            written: {
                                                bsonType: "string",
                                                description: "must be a string and is required"
                                                },
                                            spoken: {
                                                bsonType: "string",
                                                description: "must be a string and is required"
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                projectsAndAchievements: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            title: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            description: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            date: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            impact: {
                                bsonType: "string",
                                description: "must be a string"
                                }
                            }
                        }
                    },
                interestsAndHobbies: {
                    bsonType: "array",
                    items: {
                        bsonType: "string"
                        },
                    description: "must be an array of strings and is required"
                    },
                references: {
                    bsonType: "array",
                    items: {
                        bsonType: "object",
                        properties: {
                            name: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            position: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            company: {
                                bsonType: "string",
                                description: "must be a string and is required"
                                },
                            contact: {
                                bsonType: "object",
                                required: ["phone", "email"],
                                properties: {
                                    phone: {
                                        bsonType: "string",
                                        description: "must be a string and is required"
                                        },
                                    email: {
                                        bsonType: "string",
                                        pattern: "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
                                        description: "must be a valid email address and is required"
                                        }
                                    }
                                }
                            }
                        }
                    },
                attachment: {
                    bsonType: "binData",
                    description: "can be null or binary data for file attachment"
                    },
                fileStorageInfo: {
                    bsonType: "object",
                    properties: {
                        filename: {
                            bsonType: "string",
                            description: "must be a string and is required"
                            },
                        mimeType: {
                            bsonType: "string",
                            description: "must be a valid email address and is required"
                            },
                        s3Key: {
                            bsonType: "string",
                            description: "must be a valid email address and is required"
                            }
                        }
                    },
                tags: {
                    bsonType: "array",
                    items: {
                        bsonType: "string"
                        },
                    description: "must be an array of strings and is required"
                    },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                createdBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                lastUpdatedBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    }
                }
            }
        }
    }
);

db.createCollection("ResumeMatches", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "ResumeMatches",
            required: [
                "jobPostId",
                "candidateInfo",
                "tenantId",
                "aiAnalysisReportDetails",
                "status",
                "createdAt",
                "lastUpdatedAt",
                "createdBy",
                "lastUpdatedBy"
                ],
            properties: {
                jobPostId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
                jobPostTitle: {
                    bsonType: "string",
                    description: "must be a valid string"
                    },
                candidateInfo: {
                    bsonType: "object",
                    required: ["candidateId", "candidateName", "nbYearsExperience", "skills"],
                    properties: {
                        candidateId: {
                            bsonType: "string",
                            description: "must be a valid string and is required"
                            },
                        candidateName: {
                            bsonType: "string",
                            description: "must be a valid string and is required"
                            },
                        nbYearsExperience: {
                            bsonType: "int",
                            description: "must be a valid int and is required"
                            },
                        skills: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            },
                        candidateProfileSummary: {
                            bsonType: "string",
                            description: "must be a valid string and is required"
                            }
                        },
                    description: "must be a valid object and is required"
                    },
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid objectId and is required"
                    },
               clientId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
                aiAnalysisReportDetails: {
                    bsonType: "object",
                    required: ["skillsMatch", "exceedsRequirements", "lackingSkills", "experienceAlignment", "overallSummary"],
                    properties: {
                        skillsMatch: {
                            bsonType: "object",
                            description: "Details about the candidate's skills match with the job requirements",
                            properties: {
                                summary: {
                                    bsonType: "string",
                                    description: "A summary of how well the candidate's skills match the job requirements"
                                    },
                                degreeOfMatch: {
                                    bsonType: "int",
                                    description: "A percentage representing the degree of match between the candidate's skills and the job requirements"
                                    }
                                }
                            },
                        exceedsRequirements: {
                            bsonType: "object",
                            description: "Details about how the candidate's skills and work experience exceeds job requirements (if not applicable say he skills are below the job requirements)",
                            properties: {
                                summary: {
                                    bsonType: "string",
                                    description: "A summary of how the candidate's skills and work experience exceeds the job requirements (if not applicable say he skills are below the job requirements)"
                                    }
                                }
                            },
                        lackingSkills: {
                            bsonType: "object",
                            description: "Details about the skills the candidate is lacking for the job",
                            properties: {
                                summary: {
                                    bsonType: "string",
                                    description: "A summary of the skills the candidate is lacking with respect to the job requirements"
                                    }
                                }
                            },
                        experienceAlignment: {
                            bsonType: "object",
                            description: "Details about how the candidate's work experience aligns with the job requirements",
                            properties: {
                                summary: {
                                    bsonType: "string",
                                    description: "A summary of how the candidate's work experience aligns with the job"
                                    },
                                degreeOfMatch: {
                                    bsonType: "int",
                                    description: "A percentage representing the degree of match between the candidate's experience and the job requirements"
                                    }
                                }
                            },
                        overallSummary: {
                            bsonType: "object",
                            description: "An overall summary of the screening report, including score and improvement points",
                            properties: {
                                summary: {
                                    bsonType: "string",
                                    description: "A summary of the overall assessment"
                                    },
                                score: {
                                    bsonType: "int",
                                    description: "The overall score for the candidate's suitability"
                                    },
                                pointsForImprovement: {
                                    bsonType: "array",
                                    description: "A list of areas where the candidate can improve",
                                    items: {
                                        bsonType: "string"
                                        }
                                    }
                                }
                            }
                        }
                    },
                status: {
                    bsonType: "string",
                    enum: ["NEW", "SHORTLIST", "INTERVIEW", "REJECT", "OPEN", "CLOSE"],
                    description: "must be a string"
                    },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                createdBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                lastUpdatedBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    }
                }
            }
        }
    }
);

// Remove this collection
db.createCollection("InterviewQuestions", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "InterviewQuestions",
            required: [
                "tenantId",
                "jobPostId",
                "candidateInfo",
                "questionnaireDetails",
                "createdAt",
                "lastUpdatedAt",
                "createdBy",
                "lastUpdatedBy"
                ],
            properties: {
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
                jobPostId: {
                    bsonType: "objectId",
                    description: "must be a valid ObjectId and is required"
                    },
                candidateInfo: {
                    bsonType: "object",
                    required: ["candidateId", "candidateName", "nbYearsOfExperience", "skills", "candidateProfileSummary"],
                    properties: {
                        candidateId: {
                            bsonType: "objectId", // ID of the CV
                            description: "must be a valid ObjectId and is required"
                            },
                        candidateName: {
                            bsonType: "string",
                            description: "must be a valid string and is required"
                            },
                        nbYearsOfExperience: {
                            bsonType: "int",
                            description: "must be a valid int and is required"
                            },
                        skills: {
                            bsonType: "array",
                            items: {
                                bsonType: "string"
                                },
                            description: "must be an array of strings and is required"
                            },
                        candidateProfileSummary: {
                            bsonType: "string",
                            description: "must be a valid string and is required"
                            }
                        },
                    description: "must be a valid object and is required"
                    },
                questionnaireDetails: {
                    bsonType: "object",
                    required: ["skillsBasedQuestions", "strengthBasedQuestions", "gapExplorationQuestions"],
                    properties: {
                        skillsBasedQuestions: {
                            bsonType: "array",
                            description: "10 questions based on the candidate's skills; must be an array of strings and is required",
                            items: {
                                bsonType: "string"
                                }
                            },
                        strengthBasedQuestions: {
                            bsonType: "array",
                            description: "List of 10 questions based on the candidate's strengths; must be an array of strings and is required",
                            items: {
                                bsonType: "string"
                                }
                            },
                        gapExplorationQuestions: {
                            bsonType: "array",
                            description: "List of 10 questions aimed at exploring gaps in the candidate's profile; must be an array of strings and is required",
                            items: {
                                bsonType: "string"
                                }
                            }
                        },
                    description: "Suggested interview questions based on the screening",
                    },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                lastUpdatedAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                createdBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                lastUpdatedBy: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    }
                }
            }
        }
    }
);

db.createCollection("StripeEventLogs", {
    validator: {
        $jsonSchema: {
            bsonType: "object",
            title: "StripeEventLogs",
            required: [
                "tenantId",
                "eventType",
                "createdAt",
                "createdBy"
                ],
            properties: {
                tenantId: {
                    bsonType: "objectId",
                    description: "must be a valid objectId and is required"
                    },
                eventType: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                stripeCustomerId: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                stripeSubscriptionId: {
                    bsonType: "string",
                    description: "must be a valid string and is required"
                    },
                createdAt: {
                    bsonType: "date",
                    description: "must be a valid date and is required"
                    },
                lastUpdatedAt: {
                     bsonType: "date",
                     description: "must be a valid date and is required"
                },
                createdBy: {
                    bsonType: "string",
                    description: "must be a valid ObjectId and is required"
                    }
                }
            }
        }
    }
);

db.createCollection("ProductsReferences", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      title: "ProductsReferences",
      required: [
        "productName",
        "stripeProductId",
        "price",
        "createdAt"
      ],
      properties: {
        productName: {
          bsonType: "string",
          description: "must be a valid string and is required"
        },
        stripeProductId: {
          bsonType: "string",
          description: "must be a valid string and is required"
        },
        price: {
          bsonType: "object",
          required: [
            "monthlyPriceId",
            "annualPriceId",
            "monthlyPrice",
            "annuallyPrice"
          ],
          properties: {
            monthlyPriceId: {
              bsonType: "string",
              description: "must be a valid string and is required"
            },
            monthlyPrice: {
              bsonType: "number",
              description: "must be a valid number and is required"
            },
            annualPriceId: {
              bsonType: "string",
              description: "must be a valid string and is required"
            },
            annuallyPrice: {
              bsonType: "number",
              description: "must be a valid number and is required"
            }
          },
          description: "must be a valid object with monthly and annual price info"
        },
        createdAt: {
          bsonType: "date",
          description: "must be a valid date and is required"
        },
        lastUpdatedAt: {
          bsonType: "date",
          description: "must be a valid date and is required"
        }
      }
    }
  }
});

// Chats
db.createCollection("Chats", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      title: "Chats",
      required: ["tenantId", "title", "context", "participants", "createdAt", "createdBy", "status"],
      properties: {
        tenantId: { bsonType: "string", description: "Tenant identifier" },
        title: { bsonType: "string" },
        status: { enum: ["OPEN", "CLOSED", "ARCHIVED"] },
        context: {
          bsonType: "object",
          required: ["cvId", "jobPostId"],
          properties: {
            cvId: { bsonType: "objectId" },
            jobPostId: { bsonType: "objectId" },
            resumeMatchId: { bsonType: ["objectId", "null"] }
          }
        },
        participants: {
          bsonType: "array",
          items: {
            bsonType: "object",
            required: ["userId", "role"],
            properties: {
              userId: { bsonType: "objectId" },
              role: { enum: ["OWNER", "COLLABORATOR", "VIEWER"] }
            }
          },
          minItems: 1
        },
        metadata: {
          bsonType: "object",
          properties: {
            language: { bsonType: "string" },
            tags: { bsonType: "array", items: { bsonType: "string" } }
          }
        },
        createdAt: { bsonType: "date" },
        lastUpdatedAt: { bsonType: "date" },
        createdBy: { bsonType: "string" },
        lastUpdatedBy: { bsonType: "string" }
      }
    }
  }
});

// ChatMessages
db.createCollection("ChatMessages", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      title: "ChatMessages",
      required: ["tenantId", "chatId", "role", "content", "createdAt"],
      properties: {
        tenantId: { bsonType: "string" },
        chatId: { bsonType: "objectId" },
        role: { enum: ["SYSTEM", "USER", "ASSISTANT"] },
        participantId: { bsonType: ["objectId", "null"], description: "null for assistant/system" },
        content: { bsonType: "string" },
        tokens: {
          bsonType: "object",
          properties: {
            promptTokens: { bsonType: ["int", "long"] },
            completionTokens: { bsonType: ["int", "long"] },
            model: { bsonType: "string" }
          }
        },
        createdAt: { bsonType: "date" },
        metadata: {
          bsonType: "object",
          properties: {
            citations: { bsonType: "array", items: { bsonType: "string" } },
            failed: { bsonType: "bool" } // Tell if the message has failed or not
          }
        }
      }
    }
  }
});


// Clients collection (multi-tenant)
db.createCollection("Clients", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["tenantId", "name", "createdAt", "updatedAt"],
      additionalProperties: false,
      properties: {
        // Multi-tenant scope (accept ObjectId or 24-hex string)
        tenantId: {
          oneOf: [
            { bsonType: "objectId" },
            { bsonType: "string"}
          ],
          description: "Owning tenant/agency"
        },

        // Short, unique per-tenant identifier (e.g., ACME, LUX-NATIXIS)
        clientCode: {
          bsonType: "string",
          description: "Unique per tenant"
        },

        // Display/official names
        name: { bsonType: "string", minLength: 2, maxLength: 256 },

        // Email/web domains to auto-resolve employer from candidate emails
        domains: {
          bsonType: "array",
          items: { bsonType: "string" },
          uniqueItems: true
        },

        // Contacts (client-side stakeholders)
        contacts: {
          bsonType: "array",
          maxItems: 100,
          items: {
            bsonType: "object",
            additionalProperties: false,
            properties: {
              contactId: {
                oneOf: [
                  { bsonType: "objectId" },
                  { bsonType: "string" }
                ]
              },
              firstName: { bsonType: "string", maxLength: 100 },
              lastName: { bsonType: "string", maxLength: 100 },
              email: { bsonType: "string"},
              phone: { bsonType: "string", maxLength: 30 },
              role: { bsonType: "string", maxLength: 120 },
              isPrimary: { bsonType: "bool" }
            }
          }
        },

        // External system IDs (ATS/CRM/ERP)
        externalIds: {
          bsonType: "object",
          additionalProperties: { bsonType: "string", maxLength: 128 }
        },

        notes: { bsonType: "string", maxLength: 4000 },

        // Audit
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
        archivedAt: { bsonType: "date" }
      }
    }
  },
  validationLevel: "moderate",
  validationAction: "error"
});

db.createCollection("ClientReports", {
  validator: {
    $jsonSchema: {
      bsonType: "object",
      required: ["tenantId", "clientId", "title", "positionTitle",  "createdAt"],
      additionalProperties: false,
      properties: {
        // Multitenancy & ownership
        tenantId: { bsonType: "objectId", description: "Tenant/agency partition key" },
        clientId: { bsonType: "objectId", description: "Client company id" },

        // Identity & classification
        reportType: { bsonType: "string", maxLength: 160, description: "Type of report" }, // "SHORTLIST", "FULL", "SUMMARY"
        title: { bsonType: "string", minLength: 3, maxLength: 160 },
        positionTitle: { bsonType: "string", minLength: 1, maxLength: 250 },
        preparedFor: { bsonType: "string", minLength: 1, maxLength: 250 },
        preparedByUserId: { bsonType: "objectId" },

        // Branding block (client-styled)
        branding: {
          bsonType: "object",
          additionalProperties: false,
          properties: {
            logoUrl: { bsonType: "string", description: "Public or signed URL" },
            primaryColor: { bsonType: "string", pattern: "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$" },
            secondaryColor: { bsonType: "string", pattern: "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$" },
            accentColor: { bsonType: "string", pattern: "^#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$" },
            footerText: { bsonType: "string", maxLength: 400 }
          }
        },

        // Shortlist array (candidates)
        shortlist: {
          bsonType: "array",
          minItems: 0,
          items: {
            bsonType: "object",
            required: ["candidateName", "fitScore", "experienceSnapshot"],
            additionalProperties: false,
            properties: {
              // Link to internal candidate if exists
              candidateId: { bsonType: "objectId" },
              candidateName: { bsonType: "string", minLength: 1, maxLength: 160 },

              fitScore: { bsonType: "int", minimum: 0, maximum: 100 },

              strengths: {
                bsonType: "array",
                items: { bsonType: "string", maxLength: 80 },
                maxItems: 20
              },
              experienceSnapshot: { bsonType: "string", minLength: 1, maxLength: 300 },

              // Resume / CV references
              resume: {
                bsonType: "object",
                additionalProperties: false,
                properties: {
                  cvId: { bsonType: "objectId" },
                  downloadUrl: { bsonType: "string", pattern: "^(http|https)://" } // e.g., S3 signed URL or CDN
                }
              }
            }
          }
        },

        // Aggregated metrics for the report
        metrics: {
          bsonType: "object",
          required: ["totalAnalyzed", "shortlistedCount", "topFitScore", "averageFitScore"],
          additionalProperties: false,
          properties: {
            totalAnalyzed: { bsonType: "int", minimum: 0 },
            shortlistedCount: { bsonType: "int", minimum: 0 },
            topFitScore: { bsonType: "int", minimum: 0, maximum: 100 },
            averageFitScore: { bsonType: "double", minimum: 0.0, maximum: 100.0 }
          }
        },

        // Generated file artifacts (PDF/HTML)
        files: {
          bsonType: "object",
          additionalProperties: false,
          properties: {
            pdfUrl: { bsonType: "string", pattern: "^(http|https)://" },
            htmlUrl: { bsonType: "string", pattern: "^(http|https)://" },
            storageProvider: { enum: ["S3", "GCS", "AZURE_BLOB", "LOCAL"] },
            sha256: { bsonType: "string", pattern: "^[A-Fa-f0-9]{64}$" }, // optional integrity check
            fileSizeBytes: { bsonType: "long", minimum: 0 }
          }
        },

        // Lifecycle & audit
        status: { enum: ["GENERATED", "SENT", "VIEWED", "EXPIRED", "ARCHIVED"] },
        createdAt: { bsonType: "date" },
        updatedAt: { bsonType: "date" },
        version: { bsonType: "int", minimum: 1, description: "Increment on regeneration" },

        // Optional free-form notes (kept small)
        notes: { bsonType: "string", maxLength: 1000 }
      }
    }
  }
});