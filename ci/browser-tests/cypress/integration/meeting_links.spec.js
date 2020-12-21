describe('Meeting Links', function () {
    beforeEach(() => {
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

    afterEach(() => {
        cy.get(".button-with-menu button").click({"multiple":true, "force":true})
        cy.get("#delete-button").click()
        cy.get("#confirm-delete").click()
    })

    function checkCadastralUnitsSorted() {
        const search = `input[placeholder='Link...']`
        cy.get(search).type("Tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(4)").contains("Juurdepääsutee lõik 471")
        cy.get("div.select-user-list div.select-user-entry:nth-child(5)").contains("Rapla-Pärnu raudtee 471")
    }

    function checkEstatesSorted() {
        const search = `input[placeholder='Link...']`
        cy.get(search).type("36")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("4343606")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("8293650")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("11263650")
        cy.get("div.select-user-list div.select-user-entry:nth-child(4)").contains("16036950")
    }

    function checkTasksSorted() {
        // TODO:implement
    }

    function createMeeting() {
        cy.get("button.project-menu").click()
        cy.get("li.project-menu-item-project-meetings").click()
        cy.wait(1000)

        cy.contains('Preliminary design').click()
        cy.wait(1000)

        cy.contains('Create meeting').click()
        cy.get(`input[class*=':date-input']`).type(new Date().toLocaleDateString("et-EE"))
        cy.get("[class*=start-time]").type("10:00")
        cy.get("[class*=end-time]").type("11:00")
        cy.formInput(
            ":meeting/title", this.testmeeting,
            ":meeting/location", "Test environment RAM")
        cy.formSubmit()
    }

    function createAgenda() {
        let sel = "a:contains(" + this.testmeeting + ")"
        cy.get(sel).click()

        cy.get("#add-agenda").click()
        cy.formInput(":meeting.agenda/topic", this.testtopic)
        cy.get("button[type=submit]").click({"multiple": true, "force": true})
    }

    function selectSearchItems(itemType) {
        cy.get(".agenda-heading h3").contains(this.testtopic)
        cy.get(".agenda-heading h3").click()
        const select = "#links-type-select"
        const option = itemType
        cy.get(option).then(($opt) => {
            cy.get(select).select($opt.attr("value"))
        })
    }

    it('sort searched cadastral units', function () {
        cy.get("h1").contains("integration test project")
        createMeeting.call(this);
        createAgenda.call(this);
        selectSearchItems.call(this, `[data-item*=':cadastral-unit']`);
        cy.wait(1000)
        checkCadastralUnitsSorted();
        cy.wait(3000)
    })

    it('sort searched real estates', function () {
        cy.get("h1").contains("integration test project")
        createMeeting.call(this);
        createAgenda.call(this);
        selectSearchItems.call(this, `[data-item*=':estate']`);
        cy.wait(1000)
        checkEstatesSorted();
        cy.wait(3000)
    })

    it('sort searched tasks', function () {
        cy.get("h1").contains("integration test project")
        createMeeting.call(this)
        createAgenda.call(this)
        selectSearchItems.call(this, `[data-item*=':task']`)
        cy.wait(1000)
        checkTasksSorted();
        cy.wait(3000)
    })
})
