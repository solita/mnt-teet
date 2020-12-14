context('Cooperation', function() {
    before(() => {
        cy.dummyLogin("Carla")
        cy.randomName("thirdparty", "testcompany")

        const now = new Date()
        cy.wrap(now.toLocaleDateString("et-EE")).as("today")
        cy.wrap(new Date(now.getTime() + 1000 * 60 * 60 * 24 * 14).toLocaleDateString("et-EE")).as("twoWeeks")

    })

    it("Cooperation workflow", function() { // use function instead of fat arrow because we use "this"
        cy.get(".left-menu-projects-list").click()
        cy.get("td").contains("cooperation test").click()

        // check project page is rendered
        cy.get("h1").contains("cooperation test")

        // open menu and select cooperation
        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-cooperation").click()

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
        cy.get("div[data-cy-test='valid-until']");

        // EDIT THE response and remove the valid months so the valid-until should be removed
        cy.get(".edit-response").click();

        cy.get("div[data-form-attribute=':cooperation.response/valid-months'] input").clear();

        cy.formSubmit();

        // Since we cleared the valid months field this valid until should no longer exist
        cy.get("div[data-cy-test='valid-until']").should('not.exist');

    })

})
