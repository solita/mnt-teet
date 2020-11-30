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


    })

})
