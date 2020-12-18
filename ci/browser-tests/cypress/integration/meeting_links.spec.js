describe('Meeting Links Test', function () {
    before(() => {
        cy.dummyLogin("Danny")
        cy.selectLanguage("ENG")
        cy.projectByName("integration test project")
        cy.randomName("testmeeting", "testmeeting")
        cy.randomName("testtopic", "testtopic")
        cy.backendCommand(":thk.project/update-cadastral-units", "{:project-id \"18463\" :cadastral-units " +
            "#{\"2:63801:001:0241\"" +
             "\"2:92901:001:0138\"" +
            " \"2:93001:001:0019\"" +
            " \"2:63801:001:0176\"" +
            "\"2:63801:001:0172\"" +
            "\"2:93001:001:0029\"" +
            "\"2:63801:001:0239\"" +
            "\"2:63801:001:0243\"" +
            "\"2:63801:001:0238\"" +
            "\"2:92901:001:0137\"" +
            "\"2:93001:001:0071\"" +
            "\"2:93001:001:0009\"" +
            "\"2:63801:001:0237\"}}")
    })

    after(() => {
        cy.get(".button-with-menu button").click({"multiple":true, "force":true})
        cy.get("#delete-button").click()
        cy.get("#confirm-delete").click()
    })


    it('sort searched cadastral units ', function () {
        cy.get("h1").contains("integration test project")

        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-project-meetings").click()

        cy.wait(1000)

        // create test meeting
        cy.contains('Preliminary design').click()

        cy.wait(1000)

        cy.contains('Create meeting').click()

        cy.get(`input[class*=':date-input']`).type(new Date().toLocaleDateString("et-EE"))

        cy.wait(1000)

        cy.get("[class*=start-time]").type( "10:00")
        cy.get("[class*=end-time]").type("11:00" )

        cy.formInput(
            ":meeting/title", this.testmeeting,
            ":meeting/location", "Test environment RAM")
        cy.formSubmit()

        let sel = "a:contains(" + this.testmeeting + ")"
        cy.get(sel).click()

        cy.get("#add-agenda").click()

        cy.formInput(":meeting.agenda/topic", this.testtopic)
        cy.get("button[type=submit]").click({"multiple":true, "force": true})

        cy.get(".agenda-heading h3").contains(this.testtopic)
        cy.get(".agenda-heading h3").click()

        const select = "#links-type-select"
        const option = `[data-item*=':cadastral-unit']`

        cy.get(option).then(($opt) => {
            cy.get(select).select($opt.attr("value"))
        })

        cy.wait(1000)

        const search = `input[placeholder='Link...']`
        cy.get(search).type("Tee")
        cy.wait(3000)
    })
})