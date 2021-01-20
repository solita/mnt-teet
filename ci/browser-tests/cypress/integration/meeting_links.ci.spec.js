describe('Meeting Links', function () {
    beforeEach(() => {
        cy.dummyLogin("Danny")

        cy.randomName("testmeeting", "testmeeting")
        cy.randomName("testtopic", "testtopic")
        cy.randomName("testfileB", "testfileB")
        cy.randomName("testfileA", "testfileA")
        cy.randomName("testfileC", "testfileC")

        cy.setup("task", {"project-name": "MEETING TESTING",
                          "activity": "preliminary-design"})
        cy.setup("mock-cadastral-unit-link-search", {})

        //cy.selectLanguage("ENG")


    })

    function checkCadastralUnitsSorted() {
        const search = `input[placeholder='Lisan viite...']`
        cy.get(search).type("Tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("58 Aluste-Kergu tee")
        cy.get("div.select-user-list div.select-user-entry:nth-child(4)").contains("Juurdepääsutee lõik 471")
        cy.get("div.select-user-list div.select-user-entry:nth-child(5)").contains("Rapla-Pärnu raudtee 471")
    }

    function checkEstatesSorted() {
        const search = `input[placeholder='Lisan viite...']`
        cy.get(search).type("54")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains("5453150")
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains("5477850")
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains("54128140")
    }

    function uploadFile(name, fixturePath) {
        // Upload file
        let fileNameWithSuffix = name + ".jpg"
        cy.get("[data-cy=task-file-upload]").click()
        cy.uploadFile({inputSelector: "input[id=files-field]",
            fixturePath: fixturePath,
            mimeType: "image/jpeg",
            fileName: fileNameWithSuffix})
        cy.get(`input[value=${name}]`)
        cy.get("button[type=submit]").click({force: true})

        // wait for dialog to close
        cy.get("button[type=submit]").should("not.exist")
    }

    function checkTasksSorted() {
        // TODO:implement
    }

    function checkFilesSorted() {
        const search = `input[placeholder='Lisan viite...']`
        cy.get(search).type("test")
        cy.get("div.select-user-list div.select-user-entry:nth-child(1)").contains(this.testfileA)
        cy.get("div.select-user-list div.select-user-entry:nth-child(2)").contains(this.testfileB)
        cy.get("div.select-user-list div.select-user-entry:nth-child(3)").contains(this.testfileC)
    }

    function createMeeting() {
        cy.get("@task").then((t) => cy.visit("#/projects/"+t["project-id"]+"/meetings"))

        cy.contains('Eelprojekt').click()
        cy.wait(1000)

        cy.get(".project-navigator-add-meeting button").click()
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

    function openTestTopic() {
        cy.get(".agenda-heading h3").contains(this.testtopic)
        cy.get(".agenda-heading h3").click()
    }

    function selectSearchItems(itemType) {
        const select = "#links-type-select"
        cy.get(itemType).then(($opt) => {
            cy.get(select).select($opt.attr("value"))
        })
        // rerender changes the input
        cy.wait(100)
    }

    it('sort searched cadastral units', function () {
        createMeeting.call(this)
        createAgenda.call(this)
        openTestTopic.call(this)
        selectSearchItems.call(this, `[data-item*=':cadastral-unit']`);
        cy.wait(1000)
        checkCadastralUnitsSorted()
    })

    it('sort searched real estates', function () {
        createMeeting.call(this)
        createAgenda.call(this)
        openTestTopic.call(this)
        selectSearchItems.call(this, `[data-item*=':estate']`);
        cy.wait(1000)
        checkEstatesSorted()
    })

    it('sort searched files', function () {
        cy.get("@task").then((t) => cy.visit("#/projects/"+t["project-id"]))
        cy.get("li a").contains("Eelprojekt").click({force: true})
        cy.get("li a").contains("Teostatavusuuring").click({force: true})

        uploadFile(this.testfileA, "text_file.jpg")
        uploadFile(this.testfileB, "text_file2.jpg")
        uploadFile(this.testfileC, "text_file3.jpg")

        createMeeting.call(this)
        createAgenda.call(this)

        openTestTopic.call(this)
        cy.wait(1000)

        selectSearchItems.call(this, `[data-item*=':file']`)
        checkFilesSorted.call(this)


    })
})
