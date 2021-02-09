context('Cooperation', function() {
    beforeEach(() => {

        cy.request({method: "POST",
                    url: "/testsetup/task",
                    body: {"project-name": "COOPERATION TESTING"}})
            .then((response) => {
                cy.wrap(response.body["project-id"]).as("projectID")
            })

        cy.dummyLogin("Danny")
        cy.randomName("thirdparty", "testcompany")

        const now = new Date()
        cy.wrap(now.toLocaleDateString("et-EE")).as("today")
        cy.wrap(new Date(now.getTime() + 1000 * 60 * 60 * 24 * 14).toLocaleDateString("et-EE")).as("twoWeeks")

    })

    it("Cooperation workflow", function() { // use function instead of fat arrow because we use "this"

        // visit project page
        cy.visit("#/projects/"+this.projectID)

        // open menu and select cooperation
        cy.get("button.project-menu").click({force: true})
        cy.get("li.project-menu-item-cooperation").click({force: true})

        cy.get(".cooperation-overview-page")

        // Create new third party

        cy.get("button.new-third-party").click()

        cy.formInput(
            ":cooperation.3rd-party/name", this.thirdparty,
            ":cooperation.3rd-party/id-code", "123456",
            ":cooperation.3rd-party/email", "test@example.com",
            ":cooperation.3rd-party/phone", "555-1234-567890")

        cy.formSubmit()

        cy.get(".MuiSnackbar-root") // snackbar message is shown

        // new 3rd party appears in list, click it to navigate to it
        cy.get(`.cooperation-overview-page [data-third-party='${this.thirdparty}'] a`).click()


        // Navigated to 3rd party page: check that it has h1 with name of 3rd party and new application button

        cy.get(".cooperation-third-party-page h1").contains(this.thirdparty)
        cy.get("button.new-application").click()

        // fill out new application form
        cy.formInput(
            ":cooperation.application/type", "[:cooperation.application.type/work-permit]",
            ":cooperation.application/response-type", "[:cooperation.application.response-type/opinion]",
            ":cooperation.application/date", this.today,
            ":cooperation.application/response-deadline", this.twoWeeks);
        cy.formSubmit()

        // Navigated to application page:

        cy.get(".cooperation-application-page")
        cy.get(".application-people") // page has people panel


        // Fill contact information
        cy.get("[data-cy=add-contact]").click()
        cy.formInput(
            ":cooperation.contact/name", "Sir Testy Testington, esq.",
            ":cooperation.contact/company", "Test & Associates Ltd",
            ":cooperation.contact/id-code", "55667788989-0",
            ":cooperation.contact/email", "foo@example.com",
            ":cooperation.contact/phone", "123456")
        cy.formSubmit()

        // contact info is filled
        cy.get("[data-cy=add-contact]").should("not.exist")
        cy.get("[data-cy=edit-contact]")
        cy.get(".application-contact-info")


        // check we have button for entering response

        cy.get("button.enter-response").click()

        cy.formInput(
            ":cooperation.response/status", "[:cooperation.response.status/no-objection]",
            ":cooperation.response/date", this.today);

        // Check that valid until write only field does not exist when no valid months value is given
        cy.get("div[data-form-attribute=':cooperation.response/valid-until']").should('not.exist');

        // Give value to valid months to check that the valid-until input starts existing
        cy.get("div[data-form-attribute=':cooperation.response/valid-months'] input").type("12");

        // check that valid until field exists after vlaue is inputted
        cy.get("div[data-form-attribute=':cooperation.response/valid-until']");
        cy.formSubmit();

        // test that the value generated based on valid-months actually made it to the frontend
        cy.get("div[data-cy='valid-until']");

        // EDIT THE response and remove the valid months so the valid-until should be removed
        cy.get(".edit-response").click();

        cy.get("div[data-form-attribute=':cooperation.response/valid-months'] input").clear();

        cy.formSubmit();

        // Since we cleared the valid months field this valid until should no longer exist
        cy.get("div[data-cy='valid-until']").should('not.exist');

        // Submitting competent authority opinion
        cy.get(".create-opinion").click();

        cy.formInput(
            ":cooperation.opinion/status", "[:cooperation.opinion.status/partially-rejected]",
            ":cooperation.opinion/comment", "RTE:this is my comment");

        cy.formSubmit();

        // opinion has been given, the initial button should be gone
        cy.get(".create-opinion").should("not.exist");

        cy.get(".edit-opinion").click();

        cy.formInput(
            ":cooperation.opinion/status", "[:cooperation.opinion.status/rejected]");

        cy.formSubmit();


        cy.get("div[data-cy='rejected']"); // check that the opinion was changed

    })

    it("is possible to delete 3rd party without applications", function() {
        cy.wrap(`deleteme${new Date().getTime()}`).as("tpname")

        cy.visit(`#/projects/${this.projectID}/cooperation`)
        cy.get("button.new-third-party").click()
        cy.get("@tpname").then((n) => {
            cy.formInput(":cooperation.3rd-party/name", n)
            cy.formSubmit()
            cy.get(`div[data-third-party=${n}] a`).click()
            cy.get("button.edit-third-party").click()
            cy.get("button#delete-button").click()
            cy.get("button#confirm-delete").click()

            // redirect back to main cooperation page
            cy.location("hash").should("eq", `#/projects/${this.projectID}/cooperation`)
        })

    })
})
