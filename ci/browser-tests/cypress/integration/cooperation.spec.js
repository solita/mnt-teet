context('Cooperation', function() {
    before(() => {
        cy.dummyLogin("Carla")
        cy.randomName("thirdparty", "testcompany")
    })

    it("Cooperation workflow", function() {
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

        // page has h1 with name of 3rd party and new application button

        cy.get(".cooperation-third-party-page h1").contains(this.thirdparty)
        cy.get("button.new-application")

    })

})
