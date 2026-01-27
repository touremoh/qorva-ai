db.runCommand({
    collMod: "Users",
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

db.runCommand({
    collMod: "CVs",
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

db.runCommand({
    collMod: "JobsPosts",
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
                    }
                }
            }
        }
    }
);

db.runCommand({
    collMod: "ResumeMatches",
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